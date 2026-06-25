# Round 6 결제 Resilience 테스트 기록

## 구현 범위

- `POST /api/v1/payments`: 주문 기준 결제 요청 API 추가
- `POST /api/v1/payments/callback`: PG simulator 콜백 수신
- `GET /api/v1/payments/orders/{orderId}`: 결제 상태 조회 겸 PG 상태 재조회
- PG simulator 호출에 HTTP connect/read timeout 적용
- 결제 요청 경로에 CircuitBreaker 적용
- 결제 상태 조회 경로에 Retry 적용

## 설계 판단

- PG 요청 실패, timeout, circuit open은 결제 실패 확정이 아니라 `PENDING`으로 저장한다.
- 결제 생성 `POST`에는 retry를 붙이지 않는다. timeout 이후 PG가 실제로 transaction을 만들었을 수 있으므로 중복 결제 위험이 있다.
- 상태 조회 `GET`은 멱등이므로 `pgPaymentStatusLookup` retry를 적용한다.
- Resilience4j 기본 aspect 순서는 `Retry(CircuitBreaker(function))`이다. 따라서 조회 retry는 각 시도가 circuit breaker를 통과한다.
- PG simulator는 `orderId`를 6자리 이상 문자열로 요구하므로, commerce 내부 주문 ID와 PG용 주문 ID를 offset mapping으로 분리했다.

## 실제 테스트 기록

### 1차 최소 검증

- 실행 환경:
  - commerce-api: `localhost:8080`
  - commerce-api actuator: `localhost:8081`
  - pg-simulator: `localhost:8082`
  - pg-simulator actuator: `localhost:8083`
- local infra:
  - MySQL/Redis docker compose 기동
  - `paymentgateway` DB 별도 생성
- 주의:
  - pg-simulator는 공통 `jpa.yml` local profile 때문에 기본적으로 `loopers` DB로 덮일 수 있다.
  - 실제 검증에서는 simulator를 다음 옵션으로 실행했다.

```bash
./gradlew :apps:pg-simulator:bootRun --args='--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/paymentgateway'
```

### 관찰 결과

- simulator 랜덤 500 발생 시 commerce-api 결제 응답:
  - HTTP 200
  - payment status: `PENDING`
  - reason: `PG 요청 결과를 확인하지 못했습니다: InternalServerError`
- 연속 실패 후 CB 상태:
  - `resilience4j_circuitbreaker_state{name="pgPayment",state="open"} 1.0`
  - 이후 대기 후 다음 호출에서 `half_open` 상태 확인
- 성공 케이스:
  - 결제 요청 응답: `PENDING`, transactionKey 수신
  - 약 7초 뒤 callback/조회 반영: `PAID`, reason `정상 승인되었습니다.`
  - CB 메트릭: failed 2, successful 2, state closed

### Retry 보강 후 재검증

- 첫 결제 요청은 simulator 랜덤 500으로 `PENDING`.
- 두 번째 결제 요청은 transactionKey 수신.
- 7초 뒤 `GET /api/v1/payments/orders/{orderId}` 결과:
  - status: `PAID`
  - transactionKey: `20260625:TR:c5ca1e`
  - reason: `정상 승인되었습니다.`
- 메트릭:
  - `resilience4j_circuitbreaker_calls_seconds_count{kind="failed",name="pgPayment"} 1`
  - `resilience4j_circuitbreaker_calls_seconds_count{kind="successful",name="pgPayment"} 2`
  - `resilience4j_circuitbreaker_state{name="pgPayment",state="closed"} 1.0`
  - `resilience4j_retry_calls_total{kind="successful_without_retry",name="pgPaymentStatusLookup"} 1.0`

## 반복 테스트 - 2026-06-26 00:02 KST

- 목적:
  - 실제 `pg-simulator`의 40% 요청 실패, 비동기 callback, circuit breaker OPEN 동작을 여러 번 연속으로 관찰한다.
  - k6 부하 테스트 전 단계로, 기능적 resilience가 어떤 상태 분포를 만드는지 확인한다.
- 실행 방식:
  - 새 사용자, 브랜드, 상품, 주문을 매회 생성한다.
  - `POST /api/v1/payments`를 12회 반복 호출한다.
  - transactionKey를 받은 결제는 7초 뒤 `GET /api/v1/payments/orders/{orderId}`로 재조회한다.

| 시도 | 요청 결과 | transactionKey | 7초 뒤 조회 | 관찰 |
|---:|---|---|---|---|
| 1 | `PENDING` | 있음 | `PAID` | callback 반영 성공 |
| 2 | `PENDING` | 없음 | 생략 | PG 요청 500 fallback |
| 3 | `PENDING` | 있음 | `PAID` | callback 반영 성공 |
| 4 | `PENDING` | 없음 | 생략 | PG 요청 500 fallback |
| 5 | `PENDING` | 없음 | 생략 | PG 요청 500 fallback |
| 6 | `PENDING` | 없음 | 생략 | PG 요청 500 fallback |
| 7-12 | `PENDING` | 없음 | 생략 | CB `OPEN`, `CallNotPermittedException` fallback |

- 최종 Resilience4j 메트릭:
  - `resilience4j_circuitbreaker_calls_seconds_count{kind="failed",name="pgPayment"} 4`
  - `resilience4j_circuitbreaker_calls_seconds_count{kind="successful",name="pgPayment"} 4`
  - `resilience4j_circuitbreaker_state{name="pgPayment",state="open"} 1.0`
  - `resilience4j_retry_calls_total{kind="successful_without_retry",name="pgPaymentStatusLookup"} 2.0`
- DB 상태:
  - commerce `payments`
    - `PAID`: 2건
    - `PENDING` + `InternalServerError`: 5건
    - `PENDING` + `CallNotPermittedException`: 6건
  - pg-simulator `payments`
    - `SUCCESS`: 2건

## HALF_OPEN 회복 시도 - 2026-06-26 00:03 KST

- `waitDurationInOpenState=10s` 이후 새 결제를 1회 요청했다.
- 결과:
  - commerce 응답: `PENDING`
  - reason: `PG 요청 결과를 확인하지 못했습니다: InternalServerError`
  - transactionKey: 없음
- 메트릭:
  - `resilience4j_circuitbreaker_calls_seconds_count{kind="failed",name="pgPayment"} 5`
  - `resilience4j_circuitbreaker_calls_seconds_count{kind="successful",name="pgPayment"} 4`
  - `resilience4j_circuitbreaker_state{name="pgPayment",state="half_open"} 1.0`
- 해석:
  - OPEN 이후 시간이 지났다고 자동으로 CLOSED가 되는 것이 아니라, 다음 호출이 HALF_OPEN 시험 호출이 된다.
  - HALF_OPEN 시험 호출에서도 PG 500이 발생하면 회복 판단이 지연되거나 다시 OPEN으로 돌아갈 수 있다.

## 부족한 점

- CB OPEN 상태에서도 결제 row를 먼저 만들고 PG 호출 fallback을 받기 때문에, transactionKey 없는 `PENDING` 결제가 계속 쌓인다.
- transactionKey 없는 `PENDING`은 “PG에 아예 요청이 안 간 것인지”, “요청은 갔지만 응답만 못 받은 것인지” 구분이 약하다.
- callback과 status lookup이 동시에 같은 결제를 갱신할 수 있다. 현재 도메인 메서드가 이미 종료 상태면 무시하지만, DB 조건부 update 수준의 원자성은 아직 없다.
- `pgPayment` circuit breaker 하나가 결제 생성 POST와 상태 조회 GET을 같이 집계한다. 외부 PG 전체 보호에는 단순하지만, 생성 실패율과 조회 실패율을 분리해서 보기 어렵다.
- Retry는 상태 조회 GET에만 붙였지만, Resilience4j 기본 aspect 순서상 retry 시도마다 circuit breaker를 통과한다. 장애 상황에서 조회 retry가 CB 지표와 외부 호출량을 증폭시킬 수 있다.
- actuator는 prometheus만 노출되어 있어 사람이 바로 보기 쉬운 `/actuator/metrics` 조회는 현재 막혀 있다.
- 장기 `PENDING` 결제에 대한 운영 기준이 없다. 예를 들어 몇 분 뒤 재조회할지, 언제 고객에게 실패/지연 안내를 할지, 수동 확인 대상으로 넘길지 기준이 필요하다.

## 1차 개선 - PENDING 원인 분리

### 개선 목표

반복 테스트 결과, 모든 미확정 상태가 `PENDING` 하나로만 저장되어 운영 판단이 어려웠다. 따라서 결제의 최종 상태와 대기 원인을 분리했다.

- 최종 상태: `PENDING`, `PAID`, `FAILED`
- 대기 원인: `PaymentPendingReason`

### 추가한 대기 원인

| pendingReason | 의미 | 후속 처리 방향 |
|---|---|---|
| `WAITING_CALLBACK` | PG 요청은 성공했고 transactionKey를 받았으며 callback을 기다리는 상태 | callback 또는 status lookup으로 최종 상태 반영 |
| `PG_REQUEST_FAILED` | PG 결제 생성 요청이 실패한 상태 | 일정 시간 후 재시도 가능 여부 검토 |
| `PG_LOOKUP_FAILED` | PG 상태 조회 요청이 실패한 상태 | retry/reconciliation 대상 |
| `PG_LOOKUP_EMPTY` | PG 상태 조회는 성공했지만 orderId에 해당하는 결제가 없는 상태 | 생성 요청이 실제로 나가지 않았을 가능성 검토 |
| `CB_OPEN` | circuit breaker가 OPEN이라 PG를 호출하지 않은 상태 | 신규 결제 접수 제한 또는 지연 안내 검토 |
| `TIMEOUT_UNKNOWN` | timeout 등으로 실제 처리 여부를 알 수 없는 상태 | orderId 기반 PG 조회 우선 |

### 반영 내용

- `PaymentPendingReason`을 도메인에 추가했다.
- `payments.pending_reason` 컬럼으로 대기 원인을 저장한다.
- API 응답에 `pendingReason`을 포함한다.
- PG 요청 fallback에서 예외 유형별 pending reason을 분류한다.
  - `CallNotPermittedException` -> `CB_OPEN`
  - `ResourceAccessException` -> `TIMEOUT_UNKNOWN`
  - 그 외 결제 요청 실패 -> `PG_REQUEST_FAILED`
  - 그 외 상태 조회 실패 -> `PG_LOOKUP_FAILED`
- PG가 transactionKey를 반환한 `PENDING`은 `WAITING_CALLBACK`으로 기록한다.

### 개선 후 기대 효과

- transactionKey 없는 `PENDING`이 왜 생겼는지 구분할 수 있다.
- `CB_OPEN`으로 생긴 결제와 `TIMEOUT_UNKNOWN` 결제를 다른 정책으로 처리할 수 있다.
- reconciliation job을 만들 때 대상과 우선순위를 나누기 쉬워진다.
- 운영 지표를 `PENDING` 전체 건수뿐 아니라 원인별 건수로 볼 수 있다.

## 같은 주문 ID 재시도 테스트 - 2026-06-26 00:36 KST

### 테스트 질문

- 클라이언트가 같은 주문 ID로 `POST /api/v1/payments`를 재시도해도 중복 결제가 발생하지 않는가?
- PG simulator 자체가 같은 `orderId`에 대해 idempotent하지 않다면, commerce-api는 PG 결제 생성 POST를 retry하지 않는 방식으로 방어하고 있는가?
- PG 생성 요청 실패 후에는 POST retry가 아니라 어떤 경로로 실패/미확정 상태를 다뤄야 하는가?

### 자동화 테스트

- 테스트 대상:
  - `PaymentV1ApiE2ETest`
- 추가한 검증:
  - 같은 주문으로 결제 POST를 2회 호출해도 `PaymentGateway.request()`는 1회만 호출된다.
  - commerce `payments` row는 1건만 생성된다.
  - 첫 PG 생성 요청이 `TIMEOUT_UNKNOWN`으로 끝난 뒤 같은 POST를 다시 보내도 PG 생성 요청을 반복하지 않는다.
  - 미확정 결제는 `GET /api/v1/payments/orders/{orderId}` 상태 조회를 통해 복구한다.
- 실행 결과:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest"
```

- 결과:
  - `BUILD SUCCESSFUL`
  - 소요 시간: 22s

### PG simulator 직접 호출 결과

commerce-api를 거치지 않고 PG simulator에 같은 `orderId=999999`, 같은 `userId=retry-direct`로 결제 생성 POST를 반복 호출했다.

| 호출 | HTTP 결과 | transactionKey | 결과 |
|---:|---|---|---|
| 1 | 200 | `20260626:TR:e00eb7` | 생성됨 |
| 2 | 500 | 없음 | simulator 랜덤 실패 |
| 3 | 200 | `20260626:TR:70f7db` | 생성됨 |

DB 확인:

| user_id | order_id | transaction 수 |
|---|---:|---:|
| `retry-direct` | `999999` | 2 |

해석:

- PG simulator는 `orderId` 기준 idempotency를 보장하지 않는다.
- 같은 `orderId`로 PG 결제 생성 POST를 재시도하면 서로 다른 `transactionKey`가 여러 개 만들어질 수 있다.
- 따라서 commerce-api가 PG 생성 POST를 단순 retry하면 중복 결제 위험이 있다.

### commerce-api 경유 같은 주문 재시도 결과

테스트 데이터:

- user: `retryuser1`
- commerce orderId: `1`
- PG orderId mapping: `1000001`

같은 주문 ID로 `POST /api/v1/payments`를 3회 호출했다.

| 호출 | commerce 응답 status | payment id | transactionKey | 관찰 |
|---:|---|---:|---|---|
| 1 | `PENDING` | 1 | `20260626:TR:1939bf` | PG transaction 생성, callback 대기 |
| 2 | `PAID` | 1 | `20260626:TR:1939bf` | 기존 결제 반환, callback 반영됨 |
| 3 | `PAID` | 1 | `20260626:TR:1939bf` | 기존 결제 반환 |

DB 확인:

| 위치 | 조건 | row 수 |
|---|---|---:|
| commerce `payments` | `user_login_id='retryuser1' AND order_id=1` | 1 |
| pg-simulator `payments` | `user_id='retryuser1' AND order_id='1000001'` | 1 |

해석:

- 현재 commerce-api는 같은 주문 ID로 결제 POST가 재시도되면 기존 `Payment`를 먼저 조회한다.
- 기존 결제가 있으면 PG 생성 POST를 다시 보내지 않는다.
- 순차적인 클라이언트 재시도 관점에서는 중복 PG transaction이 생성되지 않았다.

### PG down 상태에서 같은 주문 재시도 결과

시나리오:

- pg-simulator를 중지한다.
- 새 commerce orderId `2`로 결제 POST를 호출한다.
- 같은 주문 ID로 POST를 한 번 더 호출한다.

| 호출 | commerce 응답 status | pendingReason | transactionKey | 관찰 |
|---:|---|---|---|---|
| 1 | `PENDING` | `TIMEOUT_UNKNOWN` | 없음 | PG 연결 실패 fallback |
| 2 | `PENDING` | `TIMEOUT_UNKNOWN` | 없음 | 기존 결제 반환, PG 생성 재시도 안 함 |

DB 확인:

| 위치 | 조건 | row 수 |
|---|---|---:|
| commerce `payments` | `user_login_id='retryuser1' AND order_id=2` | 1 |
| pg-simulator `payments` | `user_id='retryuser1' AND order_id='1000002'` | 0 |

이후 pg-simulator를 재기동하고 `GET /api/v1/payments/orders/2`를 호출했다.

| 호출 | commerce 응답 status | pendingReason | 관찰 |
|---|---|---|---|
| 상태 조회 | `PENDING` | `CB_OPEN` | 결제 생성 실패가 같은 `pgPayment` CB를 열어 조회 복구도 차단됨 |

해석:

- PG 생성 요청이 실제로 PG에 도달하지 못한 경우에도 POST retry로 PG 생성을 반복하지 않는 현재 정책은 중복 결제 방지 측면에서는 안전하다.
- 대신 사용자는 같은 주문으로 다시 POST해도 결제가 새로 시도되지 않고 기존 `PENDING`을 받는다.
- 이 경우 복구는 POST retry가 아니라 상태 조회/reconciliation으로 처리해야 한다.
- 다만 현재는 결제 생성과 상태 조회가 같은 CB를 공유해서, 생성 실패가 조회 복구까지 막는 문제가 관찰됐다.

### 현재 결론

- 같은 주문 ID에 대한 클라이언트 POST retry는 commerce-api 경유라면 순차 요청 기준으로 중복 결제를 만들지 않는다.
- PG simulator 자체는 같은 `orderId` 중복 POST를 막지 않으므로, PG 결제 생성 POST에 자동 retry를 붙이면 위험하다.
- 실패 후 대처는 다음 기준이 적절하다.
  - `WAITING_CALLBACK`: callback 대기 또는 상태 조회
  - `TIMEOUT_UNKNOWN`: 같은 주문으로 PG order 조회를 우선 시도
  - `PG_REQUEST_FAILED`: PG에 생성되지 않았을 가능성이 크므로 일정 시간 뒤 조회 후 없으면 실패/재결제 안내
  - `CB_OPEN`: PG 호출 자체를 하지 않았으므로 신규 결제 접수 제한 또는 지연 안내 검토

### 추가로 드러난 부족한 점

- 순차 재시도는 방어되지만, 완전한 동시 POST에서는 두 요청이 모두 기존 결제 없음으로 판단할 수 있다.
  - 현재 DB unique constraint가 최종 방어선 역할을 하지만, constraint 충돌을 비즈니스 응답으로 변환하는 처리는 아직 없다.
- 결제 생성 CB와 상태 조회 CB를 분리해야 한다.
  - 생성 실패로 열린 CB가 조회/reconciliation까지 막으면 복구 경로가 같이 차단된다.
- transactionKey 없는 장기 `PENDING`에 대해 최종 실패 처리 기준이 필요하다.
  - 예: N분 후 PG order 조회에도 없으면 `FAILED` 또는 `EXPIRED`로 전이하고 사용자에게 새 결제를 요구한다.

## 보완 방향

- callback/status lookup 갱신은 조건부 update로 보강한다.
  - 예: `UPDATE payments SET status = 'PAID' WHERE order_id = ? AND status = 'PENDING'`
- transactionKey 없는 `PENDING`도 orderId 기반 PG 조회로 복구하는 reconciliation job을 둔다.
- 같은 주문 ID의 동시 결제 POST에서 unique constraint 충돌을 비즈니스 응답으로 변환한다.
- 결제 생성용 CB와 상태 조회용 CB를 분리할지 검토한다.
  - 생성 CB: 사용자 결제 요청 보호
  - 조회 CB: reconciliation/polling 보호
- CB OPEN 상태에서는 신규 결제 요청을 계속 `PENDING`으로 쌓을지, 사용자에게 “결제 접수 지연” 응답을 줄지 정책을 정한다.
- PG order 조회로도 transaction이 없다고 확인된 장기 `PENDING`을 언제 실패 처리하고 재결제를 요구할지 기준을 정한다.
- 운영 지표를 명시한다.
  - `PENDING` 체류 시간
  - `PENDING` 원인별 건수
  - CB OPEN 횟수
  - retry 횟수
  - PG callback 성공/실패 건수
  - PG status lookup 성공/실패 건수
