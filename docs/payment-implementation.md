# Payment 구현 현황

## 계층 구조

```
interfaces/api/payment/   ← 미구현 (PaymentV1Controller 없음)
application/payment/      ← PaymentFacade, PaymentService, PaymentCommand, PaymentInfo
infrastructure/payment/   ← PaymentEntity, PaymentJpaRepository, PaymentRepositoryImpl
infrastructure/pg/        ← PgFeignClient, PgFeignClientConfig, PgPaymentRequest/Response
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
PENDING ──[PG 호출 성공]──▶ IN_PROGRESS ──[콜백 SUCCESS]──▶ SUCCESS
   │                            │
   │                            └──[콜백 FAILED]───▶ FAILED
   │
   └──[배치 폴링 초과]──▶ ABANDONED
                  ↑
         IN_PROGRESS도 동일
```

| 상태 | transactionKey | 의미 |
|---|---|---|
| `PENDING` | null | Payment 생성 직후, PG 호출 전 |
| `IN_PROGRESS` | 있음 | PG 호출 성공, 콜백 대기 중 |
| `SUCCESS` | 있음 | 결제 성공 |
| `FAILED` | 있음 | 결제 실패 |
| `ABANDONED` | null or 있음 | 배치 폴링 횟수 초과 포기 |

### 도메인 메서드

| 메서드 | 선행 상태 | 결과 상태 |
|---|---|---|
| `markInProgress(transactionKey)` | PENDING | IN_PROGRESS |
| `complete(status, reason)` | IN_PROGRESS | SUCCESS or FAILED |
| `abandon()` | PENDING or IN_PROGRESS | ABANDONED |
| `recordPolling()` | 제한 없음 | pollingCount++, lastPolledAt 갱신 |

---

## 결제 요청 흐름 (`PaymentFacade.requestPayment`)

```
1. orderService.getOrder(orderId)
   ├─ 없으면 → 404
   ├─ 타 유저 주문 → 403
   ├─ 주문 상태 PENDING 아님 → 409
   └─ SUCCESS Payment 존재 → 409 (중복 결제)

2. paymentService.createPayment() → Payment(PENDING, transactionKey=null) COMMIT

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

4. status == SUCCESS → orderService.confirm(orderId)
   status == FAILED  → Order 상태 변경 없음
```

---

## PG 연동 (`PgFeignClient` + `PgFeignClientConfig`)

### FeignClient

```java
POST /api/v1/payments          // 결제 요청
  Header: X-USER-ID: {userId}
  Body:   PgPaymentRequest { orderId(String), cardType, cardNo, amount, callbackUrl }
  Return: PgPaymentResponse { transactionKey, status, reason }
```

GET `/api/v1/payments/{transactionKey}` — **미구현** (배치 폴링 용도)

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
| `createPayment(...)` | @Transactional | PENDING Payment 생성 후 저장 |
| `inProgress(payment, transactionKey)` | @Transactional | PENDING → IN_PROGRESS |
| `complete(transactionKey, status, reason)` | @Transactional | IN_PROGRESS → SUCCESS/FAILED |
| `abandon(payment)` | @Transactional | PENDING/IN_PROGRESS → ABANDONED |
| `recordPolling(payment)` | @Transactional | pollingCount 증가, lastPolledAt 갱신 |
| `getByTransactionKey(transactionKey)` | readOnly | transactionKey로 조회, 없으면 404 |
| `hasSuccessPayment(orderId)` | readOnly | 중복 결제 여부 확인 |
| `findAllPendingOrInProgress()` | readOnly | 배치 폴링 대상 목록 조회 |

---

## 테스트 현황

### `PaymentTest` (도메인 단위)
- Payment 생성 → PENDING 초기 상태
- `markInProgress`: PENDING → IN_PROGRESS, 비정상 상태에서 호출 시 예외
- `complete`: IN_PROGRESS → SUCCESS/FAILED, 비정상 상태에서 호출 시 예외
- `abandon`: PENDING/IN_PROGRESS → ABANDONED, SUCCESS 상태에서 호출 시 예외
- `recordPolling`: pollingCount 증가, lastPolledAt 갱신

### `PaymentServiceIntegrationTest` (통합)
- `createPayment`: PENDING 상태로 저장
- `inProgress`: IN_PROGRESS 전환 + transactionKey 세팅
- `complete`: SUCCESS 전환 + reason/completedAt 저장
- `getByTransactionKey`: 정상 조회, NOT_FOUND
- `hasSuccessPayment`: true/false
- `findAllPendingOrInProgress`: PENDING + IN_PROGRESS만 반환

### `PaymentFacadeUnitTest` (단위, Mockito)
- `requestPayment`: NOT_FOUND / FORBIDDEN / CONFLICT(주문상태) / CONFLICT(중복결제) / PG성공 / PG실패(503)
- `receiveCallback`: SUCCESS완료+주문확정 / FAILED완료(주문무변경) / 멱등(이미SUCCESS) / 멱등(이미FAILED) / NOT_FOUND

### `PgFeignClientChaosTest` (카오스, WireMock)
- 정상 성공 → 1회 요청
- 500 × 3 → RetryableException, 3회 요청
- 500 × 2 → 성공, 3회 요청
- 500 × 1 → 성공, 2회 요청
- 네트워크 단절 → RetryableException, 3회 요청
- 타임아웃 (read 200ms) → RetryableException, 3회 요청

---

## 미구현 항목

| 항목 | 비고 |
|---|---|
| `PaymentV1Controller` | 결제 요청 `POST /api/v1/payments`, 콜백 수신 `POST /api/v1/payments/callback` |
| `PgFeignClient` GET 엔드포인트 | 배치 폴링 시 `GET /api/v1/payments/{transactionKey}` |
| 배치 폴링 Facade/Service | PENDING/IN_PROGRESS Payment 주기적 상태 확인 및 ABANDONED 처리 |