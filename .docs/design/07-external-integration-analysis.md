# 07. 외부 시스템 연동 분석 — PG 연동 트레이드오프 & 결정 기록

> PG 연동 설계 과정에서 마주한 불확실성, 트레이드오프, 결정 사항을 기록합니다.
> "왜 이렇게 했지?"를 나중에 떠올릴 때 참조하세요.

---

## 현재 구조 (Round 6 시작 시점)

```
현재 commerce-api 결제 흐름:
POST /orders/{id}/pay/start   → order.startPayment() → status: IN_PAYMENT  (PG 호출 없음)
POST /orders/{id}/pay/confirm → order.confirm()      → status: CONFIRMED   (PG 호출 없음)
```

PG 호출 자체가 없고 내부 상태만 바꾼다.
이번 주의 출발점은 **"PG를 어디서, 어떻게 호출하고, 결과를 어떻게 반영할 것인가"**다.

---

## PG Simulator 스펙 & 구조

### 기본 정보

| 항목 | 값 |
|------|---|
| 포트 | 8082 (commerce-api는 8080) |
| 언어 | Kotlin + Spring Boot |
| DB | MySQL (`paymentgateway` 스키마) |
| 요청 성공 확률 | 60% (40%는 즉시 INTERNAL_ERROR 반환) |
| 요청 지연 | 100ms ~ 500ms (동기, 응답 전 블로킹) |
| 처리 지연 (비동기) | 1s ~ 5s (요청 수락 후 별도 스레드) |
| 처리 결과 | 성공 70% / 한도초과 20% / 잘못된 카드 10% |

### API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/payments` | 결제 요청 — transactionKey 반환 |
| GET | `/api/v1/payments/{transactionKey}` | 거래 단건 조회 |
| GET | `/api/v1/payments?orderId={orderId}` | 주문에 엮인 거래 목록 조회 |

**결제 요청 파라미터**

```json
{
  "orderId": "1351039135",       // 6자리 이상 문자열
  "cardType": "SAMSUNG",         // SAMSUNG | KB | HYUNDAI
  "cardNo": "1234-5678-9814-1451", // xxxx-xxxx-xxxx-xxxx 형식
  "amount": 5000,                // 양의 정수
  "callbackUrl": "http://localhost:8080/..." // 반드시 http://localhost:8080으로 시작
}
```

> **주의:** callbackUrl은 코드에 `http://localhost:8080` prefix 검증이 하드코딩되어 있다.
> commerce-api의 콜백 엔드포인트 URL을 그대로 넘겨야 한다.

### 내부 처리 흐름 (비동기)

```
[commerce-api]                          [pg-simulator]
      │
      │  POST /api/v1/payments
      │ ─────────────────────────────────────────────────────► PaymentApi.request()
      │                                                              │
      │                                                    100~500ms 지연 (동기 블로킹)
      │                                                    40% 확률 → 즉시 500 에러 반환
      │                                                              │ (60% 통과)
      │                                                    Payment 생성 (status: PENDING)
      │                                                    PaymentEvent.PaymentCreated 발행
      │  ◄──────── { transactionKey, status: PENDING } ────────────┤
      │                                                              │ [DB 커밋 후]
      │                                                    @Async @TransactionalEventListener
      │                                                    1~5초 지연 (별도 스레드)
      │                                                              │
      │                                                    approve() 70%
      │                                                    limitExceeded() 20%
      │                                                    invalidCard() 10%
      │                                                    PaymentEvent.PaymentHandled 발행
      │                                                              │ [DB 커밋 후]
      │                                                    @Async @TransactionalEventListener
      │  POST {callbackUrl}                                          │
      │ ◄──────────────────────────────────────────────── PaymentCoreRelay.notify()
```

### Payment 엔티티 상태

```
PENDING ──── approve() ──────► SUCCESS
        └─── limitExceeded() ─► FAILED  (reason: "한도초과입니다.")
        └─── invalidCard() ───► FAILED  (reason: "잘못된 카드입니다.")
```

- 상태 전이는 `PENDING`에서만 가능 — 이미 처리된 건에 재처리 시도 시 예외
- `transactionKey`가 PK이며 `(userId, orderId, transactionKey)` unique 인덱스 존재
  → 같은 orderId로 여러 번 요청하면 **별도 거래 건으로 중복 생성됨**

### 콜백 전송 방식

```kotlin
// PaymentCoreRelay.kt
restTemplate.postForEntity(callbackUrl, transactionInfo, Any::class.java)
```

- 타임아웃 설정 없음
- 실패 시 재시도 없음 (`runCatching { }.onFailure { logger.error(...) }` 로 그냥 삼킴)
- 콜백 실패 시 PG 입장에서는 아무 일도 일어나지 않음

**콜백으로 전달되는 데이터 (TransactionInfo)**

```json
{
  "transactionKey": "20250816:TR:9577c5",
  "orderId": "1351039135",
  "status": "SUCCESS",           // SUCCESS | FAILED
  "reason": "정상 승인되었습니다.",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000
}
```

---

## 트랜잭션 경계 분석

외부 HTTP 호출과 내부 DB 커밋은 하나의 트랜잭션으로 묶을 수 없다.

```
[위험한 설계 예시]
@Transactional
public void pay() {
    order.startPayment();         // DB 커밋 예정
    pgClient.requestPayment(...)  // 외부 HTTP 호출 — 느리거나 실패할 수 있음
    orderRepository.save(order);  // 커밋
}
// 문제: PG가 느리면 트랜잭션이 장시간 열림
// 문제: PG는 요청 받았는데 내부는 롤백된 경우 발생 가능
```

**핵심 질문 (결정이 필요한 것들)**
- PG 호출 전에 내부 상태를 IN_PAYMENT로 먼저 커밋해야 하는가?
- 커밋 후 PG 호출이 실패하면 IN_PAYMENT 주문은 어떻게 처리하는가?
- PG가 처리했지만 콜백이 안 오면 내부 상태는 누가 복구하는가?

---

## 상태 기반 분석

```
내부 상태 (OrderModel)         외부 상태 (PG)
─────────────────────────     ──────────────────────────
PENDING_PAYMENT               (아직 요청 안 함)
     ↓ [결제 요청 전송]
IN_PAYMENT            ←────── PENDING
     ↓ [콜백 수신]
CONFIRMED             ←────── SUCCESS
(or FAILED)           ←────── LIMIT_EXCEEDED / INVALID_CARD
```

**상태가 어긋날 수 있는 지점**

| 시나리오 | 내부 상태 | PG 상태 | 문제 |
|----------|---------|---------|------|
| PG 요청 자체가 실패 (40%) | IN_PAYMENT | 없음 | PG에 결제건이 없는데 내부는 결제 중 |
| 콜백 URL 도달 실패 | IN_PAYMENT | SUCCESS | 실제로는 성공인데 내부는 모름 |
| 콜백 수신 후 DB 저장 실패 | IN_PAYMENT | SUCCESS | 재처리 필요, 중복 처리 위험 |
| 타임아웃으로 요청 포기 | IN_PAYMENT | PENDING or SUCCESS | PG에서 처리됐을 수도 있음 |

---

## 장애 시나리오

### 시나리오 A: PG 요청 타임아웃
- 상황: PG가 500ms 넘게 응답 없음
- 내부: IN_PAYMENT 상태
- PG: 요청이 도달했을 수도, 안 했을 수도 있음
- 문제: 이 주문을 어떻게 처리할지 판단 불가
- 복구 방법: PG의 `GET /api/v1/payments?orderId=xxx` API로 상태 조회 필요

### 시나리오 B: 콜백이 영원히 오지 않음
- 상황: PG는 내부 처리를 완료했지만 commerce-api 콜백 엔드포인트가 일시 불능
- 내부: IN_PAYMENT 상태로 영구 고착
- 문제: 사용자는 결제 결과를 알 수 없음
- 복구 방법: 주기적으로 IN_PAYMENT 주문에 대해 PG 상태 확인 (스케줄러 or 수동 API)

### 시나리오 C: CircuitBreaker 없이 PG 전체 장애
- 상황: PG 다운, 모든 결제 요청이 타임아웃 대기
- 내부: 결제 요청 스레드가 모두 PG 응답 대기로 점유
- 문제: commerce-api 전체 응답 지연 → 연쇄 장애
- 복구 방법: CircuitBreaker로 PG 호출 차단, Fallback 응답 반환

---

## 멱등성 이슈

PG는 재시도 시 동일한 결제건을 중복 생성할 수 있다.
콜백도 두 번 올 수 있다.

```
콜백이 두 번 오면?
→ order.confirm()이 두 번 호출됨
→ 현재 OrderModel.confirm()에는 상태 검증 없음
→ 이미 CONFIRMED인 주문에 또 confirm() 가능
```

`confirm()` 호출 전 **이미 CONFIRMED면 무시하는 방어 로직** 필요.

---

## 이번 주 구현 우선순위

### Must-Have

| 항목 | 이유 |
|------|------|
| Timeout 설정 | PG 지연이 내부 스레드를 점유하지 않도록 |
| CircuitBreaker | PG 장애가 commerce-api 전체로 번지지 않도록 |
| Fallback 응답 | PG가 열려있어도 사용자에게 즉시 응답 |
| 콜백 수신 엔드포인트 | 비동기 결제이므로 결과를 받을 창구가 필수 |
| 콜백 중복 처리 방어 | 콜백이 두 번 오면 confirm()이 두 번 호출됨 |

### Nice-To-Have

| 항목 | 이유 |
|------|------|
| Retryer | PG 요청 실패 시 재시도 (멱등성 전제 필요) |
| 상태 복구 API or 스케줄러 | 콜백 미수신 건을 PG 조회로 보정 |

---

## 결정 사항

### 결정 1. PG 클라이언트 기술 — FeignClient
- Must-Have인 CircuitBreaker + Timeout + Fallback 세 개를 yml 설정만으로 처리 가능
- RestTemplate 대비 Resilience4j 연동 코드량이 적음
- 에러 처리는 `ErrorDecoder`로 별도 구현 필요

### 결정 2. PAYMENT_FAILED 상태 추가
- 콜백으로 FAILED 수신 시 Order 상태를 `PAYMENT_FAILED`로 전이
- `OrderStatus` enum에 추가

### 결정 3. 콜백 엔드포인트 경로
- `POST /api/v1/payments/callback`

---

## 미결 사항

### 미결 1. PAYMENT_FAILED 주문에서 재결제 가능 여부
- `OrderModel.startPayment()`가 현재 `PENDING_PAYMENT`에서만 허용
- `PAYMENT_FAILED → IN_PAYMENT` 전이를 열어줄지 결정 필요
- 카드 변경 후 재결제 시나리오가 요구사항에 포함되는지 확인 후 결정

### 미결 2. PG 호출 실패 시 Order 상태 처리
- `startPayment()`로 IN_PAYMENT 커밋 후 PG 호출 실패 시 Order 처리 방법
- 선택 A: `PAYMENT_FAILED`로 처리
- 선택 B: PG 호출 성공 후에만 IN_PAYMENT 커밋 (트랜잭션 경계 재설계 필요)
- PaymentFacade 구현 시 결정

---
