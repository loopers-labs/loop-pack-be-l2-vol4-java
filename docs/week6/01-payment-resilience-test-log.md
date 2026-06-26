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
- callback과 status lookup이 동시에 같은 결제를 갱신할 수 있어 DB 조건부 update가 필요했다. 이후 `PENDING` 상태일 때만 최종 상태를 확정하도록 보강했다.
- `pgPayment` circuit breaker 하나가 결제 생성 POST와 상태 조회 GET을 같이 집계했다. 이후 생성 POST는 `pgPaymentRequest`, 상태 조회 GET은 `pgPaymentLookup`으로 분리했다.
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
- 당시에는 결제 생성과 상태 조회가 같은 CB를 공유해서, 생성 실패가 조회 복구까지 막는 문제가 관찰됐다.

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
- 결제 생성 CB와 상태 조회 CB를 분리했다.
  - 생성 실패로 열린 CB가 조회/reconciliation까지 막지 않도록 `pgPaymentRequest`, `pgPaymentLookup`으로 나눴다.
- transactionKey 없는 장기 `PENDING`에 대해 최종 실패 처리 기준이 필요하다.
  - 예: N분 후 PG order 조회에도 없으면 `FAILED` 또는 `EXPIRED`로 전이하고 사용자에게 새 결제를 요구한다.

## 보완 방향

- callback/status lookup 갱신은 조건부 update로 보강했다.
  - 예: `UPDATE payments SET status = 'PAID' WHERE id = ? AND status = 'PENDING'`
- transactionKey 없는 `PENDING`도 orderId 기반 PG 조회로 복구하는 reconciliation job을 둔다.
- 같은 주문 ID의 동시 결제 POST에서 unique constraint 충돌을 비즈니스 응답으로 변환한다.
- 결제 생성용 CB와 상태 조회용 CB를 분리했다.
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

## 2차 보완 검증 - 2026-06-26 11:28 KST

### 반영한 보완

- 최종 상태 확정은 DB 조건부 update로 보강했다.
  - `PENDING` 상태인 결제에만 `PAID`/`FAILED`를 반영한다.
  - callback과 status lookup이 늦게 교차 도착해도 기존 최종 상태를 덮어쓰지 못한다.
- 결제 생성 POST와 상태 조회 GET의 circuit breaker를 분리했다.
  - 생성 POST: `pgPaymentRequest`
  - 상태 조회 GET: `pgPaymentLookup`
- PG 상태 조회에서 해당 orderId 거래가 없는 404 응답은 장애가 아니라 빈 조회 결과로 분류했다.
  - `PG_LOOKUP_EMPTY`
  - retry/CB 실패로 집계하지 않는다.

### 실제 실행 환경

- commerce-api: `localhost:8080`
- commerce-api actuator: `localhost:8081`
- pg-simulator: `localhost:8082`
- pg-simulator actuator: `localhost:8083`
- pg-simulator는 별도 worktree에서 실행했다.

```bash
/bin/zsh -lc 'JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" ./gradlew :apps:pg-simulator:bootRun --args="--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/paymentgateway" --no-daemon'
./gradlew :apps:commerce-api:bootRun --no-daemon
zsh /private/tmp/payment_live_test.sh
```

원본 로그:

- `/private/tmp/payment-live-test-20260626-112844.log`
- `/private/tmp/payment-request-cb-open-test-20260626-113230.log`
- `/private/tmp/payment-cb-split-lookup-after-restart-20260626-113844.log`

### 반복 결제 요청 결과

12개 주문을 만들고 `POST /api/v1/payments`를 반복 호출했다.

| 구분 | 건수 | 관찰 |
|---|---:|---|
| `WAITING_CALLBACK` | 7 | PG transactionKey 수신, callback/status lookup으로 최종 상태 확정 가능 |
| `PG_REQUEST_FAILED` | 5 | PG simulator 랜덤 500 fallback, transactionKey 없음 |
| `CB_OPEN` | 0 | 이번 반복에서는 request CB가 열리지 않음 |

8초 후 `GET /api/v1/payments/orders/{orderId}`를 호출했다.

| 최종 commerce 상태 | 건수 | pendingReason | 관찰 |
|---|---:|---|---|
| `PAID` | 6 | 없음 | callback 또는 status lookup으로 성공 확정 |
| `FAILED` | 1 | 없음 | PG 조회 결과 실패 확정 |
| `PENDING` | 5 | `PG_LOOKUP_EMPTY` | PG에 해당 orderId transaction이 없음 |

PG simulator DB 상태:

| PG 상태 | 건수 |
|---|---:|
| `SUCCESS` | 6 |
| `FAILED` | 1 |

### 메트릭 확인

`pgPaymentRequest`:

```text
resilience4j_circuitbreaker_calls_seconds_count{kind="failed",name="pgPaymentRequest"} 5
resilience4j_circuitbreaker_calls_seconds_count{kind="successful",name="pgPaymentRequest"} 7
resilience4j_circuitbreaker_state{name="pgPaymentRequest",state="closed"} 1.0
resilience4j_circuitbreaker_state{name="pgPaymentRequest",state="open"} 0.0
```

`pgPaymentLookup`:

```text
resilience4j_circuitbreaker_calls_seconds_count{kind="failed",name="pgPaymentLookup"} 0
resilience4j_circuitbreaker_calls_seconds_count{kind="successful",name="pgPaymentLookup"} 12
resilience4j_circuitbreaker_state{name="pgPaymentLookup",state="closed"} 1.0
resilience4j_circuitbreaker_state{name="pgPaymentLookup",state="open"} 0.0
```

`pgPaymentStatusLookup` retry:

```text
resilience4j_retry_calls_total{kind="failed_with_retry",name="pgPaymentStatusLookup"} 0.0
resilience4j_retry_calls_total{kind="failed_without_retry",name="pgPaymentStatusLookup"} 0.0
resilience4j_retry_calls_total{kind="successful_without_retry",name="pgPaymentStatusLookup"} 12.0
```

해석:

- PG order 조회 404가 `PG_LOOKUP_EMPTY`로 분류되면서 retry/CB 실패로 잡히지 않았다.
- 조회 CB는 12회 모두 successful로 집계되어 `CLOSED`를 유지했다.
- 생성 POST 실패는 `pgPaymentRequest`에만 집계되고, 조회 경로의 실패율을 오염시키지 않았다.

### 생성 CB OPEN 이후 조회 분리 검증

PG simulator를 중지한 상태에서 결제 생성 요청 7회를 호출했다.

| 시도 | pendingReason | 관찰 |
|---:|---|---|
| 1-2 | `TIMEOUT_UNKNOWN` | PG 연결 실패 fallback |
| 3-7 | `CB_OPEN` | `pgPaymentRequest` OPEN 이후 호출 차단 |

메트릭:

```text
resilience4j_circuitbreaker_calls_seconds_count{kind="failed",name="pgPaymentRequest"} 7
resilience4j_circuitbreaker_state{name="pgPaymentRequest",state="open"} 1.0
```

이후 PG simulator를 다시 기동하고 같은 주문에 대해 상태 조회를 호출했다.

| 호출 | 결과 |
|---|---|
| `GET /api/v1/payments/orders/13` | `PENDING` + `PG_LOOKUP_EMPTY` |

메트릭:

```text
resilience4j_circuitbreaker_state{name="pgPaymentRequest",state="open"} 1.0
resilience4j_circuitbreaker_calls_seconds_count{kind="successful",name="pgPaymentLookup"} 13
resilience4j_circuitbreaker_state{name="pgPaymentLookup",state="closed"} 1.0
resilience4j_circuitbreaker_state{name="pgPaymentLookup",state="open"} 0.0
```

해석:

- 결제 생성 CB가 `OPEN`이어도 상태 조회 CB는 독립적으로 동작한다.
- 생성 실패로 복구 조회 경로가 같이 막히던 문제는 해소됐다.
- PG simulator 재기동 시 simulator DB가 초기화되어 기존 transaction은 없었고, 따라서 조회 결과는 `PG_LOOKUP_EMPTY`가 맞다.

### 남은 보완점

- `CB_OPEN` 상태에서는 신규 결제 row를 만들지 않고 사용자에게 결제 접수 불가 응답을 반환하도록 보강했다.
- `PG_LOOKUP_EMPTY` 실패 확정은 상태 조회 경로에 반영했다. 별도 reconciliation scheduler로 자동 마감할지는 추가 결정이 필요하다.

## 3차 보완 검증 - 2026-06-26 13:27 KST

### 보강 대상

같은 주문 ID로 `POST /api/v1/payments`가 완전히 동시에 들어오면 모든 요청이 선조회에서 “결제 없음”으로 판단할 수 있다. 기존에는 DB unique constraint가 최종 방어선이었지만, 충돌이 API 응답으로 변환되지 않아 일부 요청이 500으로 끝날 수 있었다.

### 선택지와 결정

| 선택지 | 방식 | 장점 | 트레이드오프 |
|---|---|---|---|
| A | payment row를 idempotency marker로 먼저 확정하고, unique 충돌 시 기존 payment를 반환 | 기존 “같은 주문은 같은 결제 반환” 정책과 일관적이고 PG 중복 호출을 막는다 | 생성과 결과 반영 트랜잭션을 분리해야 하며, 조건부 update가 필요하다 |
| B | 주문 row에 비관적 락을 잡고 결제 생성 전체를 직렬화 | 이해하기 쉽고 충돌 자체가 줄어든다 | PG 호출 지연이 락 대기와 주문 단위 병목으로 전파될 수 있다 |
| C | unique 충돌을 `409 CONFLICT`로 반환 | 구현이 가장 단순하다 | 사용자는 재시도해도 성공 응답을 받지 못하고, 현재 idempotent retry 정책과 맞지 않는다 |

최종 선택은 A다. 결제 생성 요청은 외부 PG 호출을 포함하므로 주문 row 락으로 PG 지연을 묶기보다, DB unique constraint를 최종 idempotency guard로 쓰는 편이 낫다. 또한 이미 순차 재시도에서 기존 결제를 반환하는 정책을 갖고 있으므로, 동시 요청도 같은 응답 정책으로 수렴시키는 것이 API 의미상 자연스럽다.

주의할 점은 후속 동시 요청이 최초 요청의 PG 결과 확정까지 기다리지는 않는다는 것이다. 후속 요청은 먼저 확정된 payment marker의 현재 상태를 반환하므로, 최초 PG 요청이 아직 진행 중이면 `PENDING`을 받을 수 있다. 이 경우 최종 상태 확인은 기존처럼 상태 조회/reconciliation 경로가 맡는다.

### 구현 메모

- `PaymentCreationResult`를 추가해 `created=true/false`를 구분한다.
- `PaymentRepository.create()`는 payment row 생성을 PG 호출 전에 확정한다.
- duplicate key가 `idx_payments_user_order`이면 기존 payment를 조회해 `created=false`로 반환한다.
- `PaymentFacade`는 `created=false`일 때 PG 생성 요청을 보내지 않고 기존 payment를 응답한다.
- PG 결과 반영은 `id` 기준 JPQL 조건부 update로 수행한다.
  - `where id = ? and status = PENDING`
  - 이미 callback/status lookup으로 최종 상태가 된 payment는 뒤늦은 결과가 덮어쓰지 못한다.

### 검증 결과

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest$RequestPayment.returnsExistingPayment_withoutRequestingPgAgain_whenSameOrderPaymentIsRequestedConcurrently" --no-daemon
```

결과: 성공.

검증한 조건:

- 5개 thread가 같은 주문으로 동시에 결제 요청.
- 모든 응답은 `200 OK`.
- 모든 응답의 payment id는 동일.
- PG 결제 생성 요청은 1회만 호출.
- `payments` row는 1건만 생성.

회귀 확인:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest" --tests "com.loopers.domain.payment.*" --tests "com.loopers.infrastructure.payment.*" --no-daemon
```

결과: 성공.

## 4차 보완 검증 - 2026-06-26 13:34 KST

### 보강 대상

`PG_LOOKUP_EMPTY`는 PG 상태 조회가 성공했지만 해당 orderId 거래가 없다는 뜻이다. 이전까지는 이 상태도 계속 `PENDING`으로 남았기 때문에, PG에 실제 거래가 없다고 확인된 결제가 장기 미확정 상태로 남을 수 있었다.

### 선택지와 결정

| 선택지 | 방식 | 장점 | 트레이드오프 |
|---|---|---|---|
| A | 첫 `PG_LOOKUP_EMPTY` 즉시 `FAILED` 처리 | 미확정 결제가 가장 빨리 사라진다 | PG 저장/조회 전파 지연이 있으면 너무 이르게 실패 처리할 수 있다 |
| B | 설정된 유예시간 이후 `PG_LOOKUP_EMPTY`면 `FAILED` 처리 | PG 지연 가능성을 흡수하면서 장기 `PENDING`을 줄인다 | 유예시간 설정이 필요하고, 상태 조회나 reconciliation이 다시 돌아야 확정된다 |
| C | API 동작은 유지하고 운영 배치에서만 처리 | 사용자 요청 경로의 동작 변화가 작다 | 배치가 없으면 문제는 계속 남고, 현재 보강 효과를 테스트하기 어렵다 |

최종 선택은 B다. PG 생성 직후에는 거래 생성/조회 반영 사이의 짧은 지연 가능성을 배제하기 어렵기 때문에 즉시 실패보다 유예시간을 두는 편이 안전하다. 반대로 계속 `PENDING`으로 두면 운영자가 수동으로 구분해야 하므로, 기본값을 두고 설정으로 조정할 수 있게 했다.

실무에서는 PG/네트워크/내부 이벤트 전파 지연을 고려해 보수적으로 2-5분 정도를 둘 수 있다고 본다. 다만 이 프로젝트는 테스트와 학습 목적이 강하고, pg-simulator 내부 동작도 짧은 편이라 기본값은 10초로 잡았다. 확인한 simulator 동작은 다음과 같다.

- 결제 생성 API는 100-500ms 랜덤 지연 후 40% 확률로 실패한다.
- 생성에 성공한 결제는 commit 이후 비동기 이벤트 리스너에서 1-5초 랜덤 sleep 후 승인/실패 처리와 callback을 진행한다.
- 따라서 simulator 정상 callback 지연 상한인 5초에 여유를 둔 10초면 학습용 마감 기준으로 충분하다.

기본 설정:

```yaml
loopers:
  payment:
    lookup-empty-failure-delay: 10s
```

### 구현 메모

- `Payment.failIfLookupEmptyGracePeriodElapsed(now, gracePeriod)`를 추가했다.
  - `PENDING + PG_LOOKUP_EMPTY`인 결제만 대상이다.
  - `createdAt + gracePeriod <= now`이면 `FAILED`로 전이한다.
  - 최종 상태(`PAID`, `FAILED`)나 다른 pending reason은 건드리지 않는다.
- `PaymentFacade.syncPayment()`에서 PG 조회 결과 반영 후 유예시간 만료 여부를 평가한다.
- 유예시간은 `PaymentProperties`의 `loopers.payment.lookup-empty-failure-delay`로 설정한다.

### 검증 결과

Red 확인:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.payment.PaymentTest$FailLookupEmpty" --no-daemon
```

결과: 도메인 메서드가 없어 컴파일 실패.

Green 확인:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.payment.PaymentTest$FailLookupEmpty" --no-daemon
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest$RequestPayment.marksPaymentFailed_whenLookupEmptyGracePeriodHasElapsed" --no-daemon
```

결과: 성공.

회귀 확인:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.payment.*" --tests "com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest" --tests "com.loopers.infrastructure.payment.*" --no-daemon
```

결과: 성공.

검증한 조건:

- 유예시간이 남은 `PG_LOOKUP_EMPTY`는 `PENDING`을 유지한다.
- 유예시간이 지난 `PG_LOOKUP_EMPTY`는 `FAILED`로 전이한다.
- API 상태 조회에서 PG가 empty를 반환하고 유예시간이 지났으면 응답도 `FAILED`가 된다.
- PG 생성 요청은 반복하지 않고, 상태 조회는 1회만 수행된다.

## 5차 보완 검증 - 2026-06-26 13:52 KST

### 보강 대상

결제 생성 circuit breaker가 `OPEN`이면 PG 호출은 실제로 나가지 않는다. 이전 구조는 payment row를 먼저 만든 뒤 PG 요청 fallback으로 `PENDING + CB_OPEN`을 저장했기 때문에, 사용자는 같은 주문으로 다시 결제를 시도해도 기존 `PENDING`만 받게 된다.

`TIMEOUT_UNKNOWN`과 `CB_OPEN`은 다르게 다루는 것이 맞다.

- `TIMEOUT_UNKNOWN`: PG에 요청을 보냈지만 응답을 못 받은 상태다. PG가 처리했을 수 있으므로 payment row를 남기고 orderId 조회로 복구한다.
- `CB_OPEN`: 애플리케이션 내부에서 PG 호출을 차단한 상태다. PG가 처리했을 가능성이 없으므로 신규 payment row를 남기지 않는다.

### 선택지와 결정

| 선택지 | 방식 | 장점 | 트레이드오프 |
|---|---|---|---|
| A | payment row 생성 전 request CB 상태를 확인하고 OPEN이면 503 반환 | PG 미호출 상태에서 가짜 `PENDING`이 쌓이지 않고, 사용자가 나중에 같은 주문으로 재시도할 수 있다 | CB 상태 확인과 실제 호출 사이의 짧은 race는 남는다 |
| B | row를 만들고 `CB_OPEN`이면 즉시 `FAILED` 처리 | 결제 시도 이력은 남는다 | 실제 PG 요청이 없던 건을 실패 결제로 저장하고, 재결제 UX가 애매해진다 |
| C | 기존처럼 `PENDING + CB_OPEN` 유지 | 구현 변경이 작다 | 복구 가능한 pending처럼 보이지만 실제로는 PG에 요청이 없어서 운영/사용자 모두 혼란스럽다 |

최종 선택은 A다. 멀티 PG fallback이 없는 현재 구조에서는 request CB가 OPEN이면 결제 접수를 받지 않는 것이 자연스럽다. 이미 존재하는 payment가 있으면 기존 payment를 반환하지만, 새 payment가 필요한 상황에서는 row 생성 전에 `SERVICE_UNAVAILABLE`로 응답한다.

### 구현 메모

- `PaymentGateway.isRequestAvailable()` 포트를 추가했다.
- `PgSimulatorPaymentGateway`는 Resilience4j `CircuitBreakerRegistry`에서 `pgPaymentRequest` 상태를 읽는다.
  - `OPEN`, `FORCED_OPEN`이면 요청 불가로 본다.
  - `CLOSED`, `HALF_OPEN` 등은 요청 가능으로 본다.
- `PaymentFacade.requestNewPayment()`는 payment row 생성 전에 request 가능 여부를 확인한다.
- 불가하면 `503 SERVICE_UNAVAILABLE`과 `"PG 결제 요청이 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요."` 메시지를 반환한다.

주의점:

- CB 상태 확인과 실제 PG 호출 사이의 race는 이론적으로 남는다.
- 이 경우 실제 호출 시점에 `CallNotPermittedException`이 날 수 있다. 현재 보강은 “이미 OPEN인 상태에서 신규 row를 만들지 않는 문제”를 우선 해결한 것이며, 호출 직전 상태 전환까지 완전히 닫으려면 row 생성과 PG 호출 순서/보상 삭제 정책을 더 크게 조정해야 한다.

### 검증 결과

Red 확인:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest$RequestPayment.returnsServiceUnavailable_withoutCreatingPayment_whenPaymentRequestCircuitBreakerIsOpen" --no-daemon
```

결과: 실패. 기존 구현은 503을 반환하지 않고 payment row 생성 흐름으로 들어갔다.

Green 확인:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest$RequestPayment.returnsServiceUnavailable_withoutCreatingPayment_whenPaymentRequestCircuitBreakerIsOpen" --no-daemon
```

결과: 성공.

회귀 확인:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.payment.*" --tests "com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest" --tests "com.loopers.infrastructure.payment.*" --no-daemon
```

결과: 성공.

검증한 조건:

- request CB가 OPEN으로 간주되면 `POST /api/v1/payments`는 `503 SERVICE_UNAVAILABLE`을 반환한다.
- PG request 호출은 0회다.
- `payments` row는 생성되지 않는다.
