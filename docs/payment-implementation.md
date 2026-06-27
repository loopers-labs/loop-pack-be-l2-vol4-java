# Payment 구현 현황

## 계층 구조

```
interfaces/api/payment/   ← PaymentV1Controller
application/payment/      ← PaymentFacade, PaymentService, PaymentPollingScheduler
                             PaymentCommand, PaymentInfo
infrastructure/payment/   ← PaymentEntity, PaymentJpaRepository, PaymentRepositoryImpl
infrastructure/pg/        ← PgFeignClient, PgFeignClientConfig
                             PgPaymentRequest, PgPaymentResponse, PgPaymentStatusResponse
                             PgApiResponse<T>
domain/payment/           ← Payment, PaymentRepository, PaymentStatus, CardType
```

---

## 도메인 모델 (`Payment`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | Long | 결제 요청 유저 |
| `orderId` | Long | 결제 대상 주문 |
| `transactionKey` | String | PG 트랜잭션 키, PG 호출 전 null |
| `cardType` | CardType | SAMSUNG / KB / HYUNDAI |
| `cardNo` | String | xxxx-xxxx-xxxx-xxxx |
| `amount` | Long | 결제 금액 |
| `status` | PaymentStatus | 현재 상태 |
| `reason` | String | 완료 사유 (콜백 메시지) |
| `pollingCount` | int | 배치 폴링 횟수 |
| `lastPolledAt` | ZonedDateTime | 마지막 폴링 시각 |
| `completedAt` | ZonedDateTime | 완료 시각 |

### 상태 전이

```
CREATED ──[PG 호출 성공]──▶ IN_PROGRESS ──[콜백 SUCCESS]──▶ SUCCESS
   │                            │
   │                            └──[콜백 FAILED]───▶ FAILED
   │
   └──[배치 폴링 초과]──▶ POLLING_EXHAUSTED
                  ↑
         IN_PROGRESS도 동일
```

| 상태 | transactionKey | 의미 |
|---|---|---|
| `CREATED` | null | Payment 생성 직후, PG 호출 전 |
| `IN_PROGRESS` | 있음 | PG 호출 성공, 콜백 대기 중 |
| `SUCCESS` | 있음 | 결제 성공 |
| `FAILED` | 있음 | 결제 실패 |
| `POLLING_EXHAUSTED` | null or 있음 | 배치 폴링 횟수 초과 포기 |

### 도메인 메서드

| 메서드 | 선행 상태 | 결과 상태 |
|---|---|---|
| `markInProgress(transactionKey)` | CREATED | IN_PROGRESS |
| `complete(status, reason)` | IN_PROGRESS | SUCCESS or FAILED |
| `abandon()` | CREATED or IN_PROGRESS | POLLING_EXHAUSTED |
| `recordPolling()` | 제한 없음 | pollingCount++, lastPolledAt 갱신 |

---

## 결제 요청 흐름 (`PaymentFacade.requestPayment`)

```
1. orderService.getOrder(orderId)
   ├─ 없으면 → 404
   ├─ 타 유저 주문 → 403
   ├─ 주문 상태 CREATED 아님 → 409
   └─ SUCCESS Payment 존재 → 409 (중복 결제)

2. paymentService.createPayment() → Payment(CREATED, transactionKey=null) COMMIT

3. pgFeignClient.requestPayment()   ← 트랜잭션 외부
   ├─ orderId: String.format("%06d", orderId)  ← zero-padding (6자리 보장)
   ├─ X-USER-ID: userId
   └─ 500 → 최대 3회 재시도, 소진 시 503
      그 외 실패 → 503

4. paymentService.inProgress(payment, transactionKey) → IN_PROGRESS COMMIT

5. transactionKey 반환 (201)
```

### 트랜잭션 경계

```
[createPayment]  → COMMIT
── PG HTTP 호출 (트랜잭션 외부) ──
[inProgress]     → COMMIT
```

---

## 콜백 수신 흐름 (`PaymentFacade.receiveCallback`)

```
1. paymentService.getByTransactionKey(transactionKey)
   └─ 없으면 → 404

2. Payment 상태가 이미 SUCCESS or FAILED → return (멱등 처리)

3. paymentService.complete(transactionKey, status, reason)

4. status == SUCCESS → orderService.confirm(orderId)  ← Order CONFIRMED
   status == FAILED  → Order 상태 변경 없음 (CREATED 유지)
```

> **설계 변경 주의**: `05-pg-integration.md`에는 FAILED 시 Order가 CANCELED로 전환된다고 명시되어 있으나, 실제 구현은 Order를 변경하지 않는다. 실패 결제 후 재결제를 허용하기 위한 의도적 차이.

---

## PG 연동 (`PgFeignClient` + `PgFeignClientConfig`)

### FeignClient

```java
POST /api/v1/payments
  Header: X-USER-ID: {userId}
  Body:   PgPaymentRequest { orderId(String), cardType, cardNo, amount, callbackUrl }
  Return: PgApiResponse<PgPaymentResponse> { data.transactionKey, data.status, data.reason }

GET /api/v1/payments/{transactionKey}
  Header: X-USER-ID: {userId}
  Return: PgApiResponse<PgPaymentStatusResponse> { data.transactionKey, data.orderId, data.cardType, data.cardNo, data.amount, data.status, data.reason }
```

### PG 응답 래퍼 (`PgApiResponse<T>`)

PG는 모든 응답을 `{"meta":{...},"data":{...}}` 구조로 반환한다.
`PgApiResponse<T>`로 래핑하여 `data()` 필드로 실제 페이로드에 접근한다.

```java
public record PgApiResponse<T>(T data) {}
```

### Retry 정책

| 항목 | 값 |
|---|---|
| 초기 대기 | 100ms |
| 최대 대기 | 500ms |
| 총 시도 횟수 | 3회 (초기 1 + 재시도 2) |
| 재시도 대상 | 5xx 응답 (`ErrorDecoder` → `RetryableException`) |
| 재시도 소진 | `FeignException` catch → 503 반환 |

---

## 서비스 메서드 목록 (`PaymentService`)

| 메서드 | 트랜잭션 | 설명 |
|---|---|---|
| `createPayment(...)` | @Transactional | CREATED Payment 생성 후 저장 |
| `inProgress(payment, transactionKey)` | @Transactional | CREATED → IN_PROGRESS |
| `complete(transactionKey, status, reason)` | @Transactional | IN_PROGRESS → SUCCESS/FAILED |
| `abandon(payment)` | @Transactional | CREATED/IN_PROGRESS → POLLING_EXHAUSTED |
| `recordPolling(payment)` | @Transactional | pollingCount 증가, lastPolledAt 갱신 |
| `getByTransactionKey(transactionKey)` | readOnly | transactionKey로 조회, 없으면 404 |
| `hasSuccessPayment(orderId)` | readOnly | 중복 결제 여부 확인 |
| `findAllPendingOrInProgress()` | readOnly | 배치 폴링 대상 목록 조회 |

---

## API 계층 (`PaymentV1Controller`)

| 메서드 | 경로 | 상태 코드 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/payments` | 201 | 결제 요청, `@LoginUser`로 userId 주입 |
| `POST` | `/api/v1/payments/callback` | 200 | PG 콜백 수신, 인증 필터 제외 |

### UserAuthFilter 설정

```java
// /api/v1/payments/callback → 인증 제외 (PG가 호출)
// /api/v1/payments/**       → 인증 필요 (유저가 호출)
```

---

## PG 폴링 스케줄러 (`PaymentPollingScheduler`)

```
@Scheduled(fixedDelay = 60_000)  — 60초 주기
MAX_POLLING_COUNT = 5
```

### 처리 흐름

```
findAllPendingOrInProgress()
  └─ 각 Payment 별 processPayment()
       ├─ CREATED
       │    └─ recordPolling() → pollingCount 증가
       │         └─ pollingCount >= MAX → abandon() → POLLING_EXHAUSTED
       │
       └─ IN_PROGRESS
            └─ pgFeignClient.getPaymentStatus()   ← PG 먼저 조회
                 ├─ 예외 발생 → warn 로그, recordPolling 미호출, 다음 결제 처리 계속
                 └─ 성공 시
                      └─ recordPolling() → pollingCount 증가
                           ├─ pollingCount >= MAX → abandon() → POLLING_EXHAUSTED
                           └─ status == SUCCESS → complete() + orderService.confirm()
                              status == FAILED  → complete() (주문 미처리)
                              status == CREATED → 다음 폴링 대기
```

> **IN_PROGRESS 처리 순서**: PG 조회가 성공해야만 폴링 횟수가 증가한다.
> PG 응답 실패 시 폴링 횟수를 증가시키지 않고 다음 주기에 재시도한다.

---

## 로컬 실행 순서

```bash
# 1. 인프라 실행
docker-compose -f ./docker/infra-compose.yml up

# 2. pg-simulator 먼저 실행 (별도 터미널)
#    paymentgateway DB를 별도로 사용해야 payments 테이블 스키마 충돌 방지
./gradlew :apps:pg-simulator:bootRun \
  --args="--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:42631/paymentgateway \
          --datasource.mysql-jpa.main.username=loopers \
          --datasource.mysql-jpa.main.password=loopers!!!"

# 3. commerce-api 실행 (별도 터미널)
./gradlew :apps:commerce-api:bootRun
```

> **주의**: pg-simulator와 commerce-api가 모두 `payments` 테이블 이름을 사용하지만 스키마가 다르다.
> pg-simulator는 `paymentgateway` DB, commerce-api는 `loopers` DB를 사용해 격리한다.
> `paymentgateway` DB는 Docker MySQL에 미리 생성 필요:
> ```sql
> CREATE DATABASE IF NOT EXISTS paymentgateway CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
> GRANT ALL PRIVILEGES ON paymentgateway.* TO 'loopers'@'%';
> ```

---

## 테스트 현황

### `PaymentTest` (도메인 단위)
- Payment 생성 → CREATED 초기 상태
- `markInProgress`: CREATED → IN_PROGRESS, 비정상 상태에서 호출 시 예외
- `complete`: IN_PROGRESS → SUCCESS/FAILED, 비정상 상태에서 호출 시 예외
- `abandon`: CREATED/IN_PROGRESS → POLLING_EXHAUSTED, SUCCESS 상태에서 호출 시 예외
- `recordPolling`: pollingCount 증가, lastPolledAt 갱신

### `PaymentServiceIntegrationTest` (통합)
- `createPayment`: CREATED 상태로 저장
- `inProgress`: IN_PROGRESS 전환 + transactionKey 세팅
- `complete`: SUCCESS 전환 + reason/completedAt 저장
- `getByTransactionKey`: 정상 조회, NOT_FOUND
- `hasSuccessPayment`: true/false
- `findAllPendingOrInProgress`: CREATED + IN_PROGRESS만 반환

### `PaymentFacadeUnitTest` (단위, Mockito)
- `requestPayment`: NOT_FOUND / FORBIDDEN / CONFLICT(주문상태) / CONFLICT(중복결제) / PG성공 / PG실패(503)
- `receiveCallback`: SUCCESS완료+주문확정 / FAILED완료(주문무변경) / 멱등(이미SUCCESS) / 멱등(이미FAILED) / NOT_FOUND

### `PaymentPollingSchedulerUnitTest` (단위, Mockito)
- 처리 대상 없음 → 폴링 수행 안 함
- CREATED + 폴링 < MAX → 폴링 기록만, PG 미조회
- 폴링 횟수 == MAX → POLLING_EXHAUSTED 처리
- IN_PROGRESS + PG SUCCESS → complete() + confirm()
- IN_PROGRESS + PG FAILED → complete(), confirm() 미호출
- PG 조회 중 예외 → recordPolling 미호출, 다음 결제 처리 계속

### `PgFeignClientChaosTest` (카오스, WireMock)
- 정상 성공 → 1회 요청
- 500 × 3 → RetryableException, 3회 요청
- 500 × 2 → 성공, 3회 요청
- 500 × 1 → 성공, 2회 요청
- 네트워크 단절 → RetryableException, 3회 요청
- 타임아웃 → RetryableException, 3회 요청

### `PaymentV1ApiE2ETest` (E2E, TestRestTemplate + `@MockBean PgFeignClient`)
- 인증 헤더 없음 → 401
- 존재하지 않는 주문 → 404
- 타 유저의 주문 → 403
- 이미 결제 완료된 주문 → 409
- PG 성공 → 201 + transactionKey
- PG 실패 → 503
- 콜백 - 존재하지 않는 transactionKey → 404
- 콜백 - SUCCESS → 200
- 콜백 - FAILED → 200
- 콜백 - 이미 처리된 건 → 200 (멱등)

---

## 미해결 리스크 (추후 검토)

| # | 항목 | 현황 | 비고 |
|---|---|---|---|
| - | Order ID TSID 적용 | 미구현 | 현재 IDENTITY 전략, Order 엔티티에서 BaseEntity 상속 끊고 TSID 생성기 적용 필요 |
| 1 | TX2(inProgress) 실패 시 복구 불가 | 미해결 | PG 성공 + DB 실패 시 Payment CREATED 고착, 콜백/폴링 모두 처리 불가 |
| 5 | `OrderService.confirm()` 멱등성 미검증 | 미검증 | 콜백+폴링 동시 도달 시 confirm() 중복 호출 가능성 |
