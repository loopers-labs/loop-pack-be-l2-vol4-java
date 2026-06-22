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
| 포트 | 8082 (commerce-api는 8080), 관리용 포트 8083 |
| 언어 | Kotlin + Spring Boot |
| DB | MySQL (`paymentgateway` 스키마) |
| 요청 성공 확률 | 60% (40%는 즉시 INTERNAL_ERROR 반환) |
| 요청 지연 | 100ms ~ 500ms (동기, 응답 전 블로킹) |
| 처리 지연 (비동기) | 1s ~ 5s (요청 수락 후 별도 스레드) |
| 처리 결과 | 성공 70% / 한도초과 20% / 잘못된 카드 10% |
| 비동기 실행 모델 | `@EnableAsync`만 선언, 커스텀 `Executor` 빈 없음 → Spring Boot 기본 `ThreadPoolTaskExecutor`(core 8 / 무제한 큐) 사용 |

> 소스: `apps/pg-simulator/src/main/kotlin/com/loopers/`

### 패키지 구조 (레이어드 아키텍처)

```
interfaces.api.payment      PaymentApi (Controller), PaymentDto (요청/응답 + 자체 검증)
interfaces.event.payment    PaymentEventListener (@Async 비동기 처리 트리거)
application.payment         PaymentApplicationService, PaymentCommand, TransactionInfo, OrderInfo
domain.payment              Payment (엔티티), PaymentEvent, TransactionStatus, CardType,
                             PaymentRepository / PaymentEventPublisher / PaymentRelay (인터페이스),
                             TransactionKeyGenerator
infrastructure.payment      PaymentCoreRepository(JPA 구현), PaymentCoreEventPublisher(스프링 이벤트 구현),
                             PaymentCoreRelay(RestTemplate 콜백 구현)
```

domain은 `Repository`/`EventPublisher`/`Relay`를 인터페이스로만 알고, infrastructure가 JPA/Spring Event/RestTemplate로 구현하는 포트-어댑터 구조다. commerce-api와 동일한 패턴.

### API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/payments` | 결제 요청 — transactionKey 반환 |
| GET | `/api/v1/payments/{transactionKey}` | 거래 단건 조회 |
| GET | `/api/v1/payments?orderId={orderId}` | 주문에 엮인 거래 목록 조회 |

**결제 요청 파라미터**

```json
{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "callbackUrl": "http://localhost:8080/..."
}
```

**검증 규칙 (`PaymentDto.PaymentRequest.validate()`, 컨트롤러 진입 직후 — 지연/실패 확률 적용 전)**

| 필드 | 규칙 | 위반 시 |
|------|------|---------|
| `orderId` | blank 아니고 길이 ≥ 6 | `BAD_REQUEST` "주문 ID는 6자리 이상 문자열이어야 합니다." |
| `cardNo` | 정규식 `^\d{4}-\d{4}-\d{4}-\d{4}$` | `BAD_REQUEST` "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다." |
| `amount` | > 0 | `BAD_REQUEST` "결제금액은 양의 정수여야 합니다." |
| `callbackUrl` | `http://localhost:8080` 로 시작 | `BAD_REQUEST` "콜백 URL 은 http://localhost:8080 로 시작해야 합니다." |

> `amount > 0` 검증은 `PaymentCommand.CreateTransaction.validate()`에서도 한 번 더 일어난다 (`PaymentApplicationService.createTransaction()` 진입 시). DTO 레이어와 도메인 커맨드 레이어에 동일 규칙이 중복돼 있다 — pg-simulator 자체 설계의 사소한 중복이며 commerce-api에서 신경 쓸 부분은 아니다.

> **주의:** `callbackUrl`은 `http://localhost:8080` prefix가 하드코딩 검증돼 있어, commerce-api 외 다른 호스트/포트로는 콜백을 보낼 수 없다.

### Payment 엔티티

```kotlin
@Entity
@Table(
    name = "payments",
    indexes = [
        Index(columnList = "user_id, transaction_key"),
        Index(columnList = "user_id, order_id"),
        Index(columnList = "user_id, order_id, transaction_key", unique = true),
    ]
)
class Payment(
    @Id val transactionKey: String,   // PK, TransactionKeyGenerator가 생성
    val userId: String,
    val orderId: String,
    val cardType: CardType,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
) {
    var status: TransactionStatus = PENDING   // private set — approve/invalidCard/limitExceeded로만 변경
    var reason: String? = null
    var createdAt: LocalDateTime = now()
    var updatedAt: LocalDateTime = now()
}
```

**상태 전이**

```
PENDING ──── approve() ──────► SUCCESS  (reason: "정상 승인되었습니다.")
        └─── limitExceeded() ─► FAILED  (reason: "한도초과입니다. 다른 카드를 선택해주세요.")
        └─── invalidCard() ───► FAILED  (reason: "잘못된 카드입니다. 다른 카드를 선택해주세요.")
```

- 세 전이 메서드 모두 진입 시 `status != PENDING`이면 `INTERNAL_ERROR`를 던지는 가드가 있다. 다만 이 메서드들은 `PaymentApplicationService.handle()` 내부에서만, 이벤트 리스너를 통해 transactionKey당 정확히 1번만 호출되도록 설계돼 있어 — pg-simulator 내부에서는 이 가드가 실제로 발동할 경로가 없다. "이중 안전장치"에 가깝다.
- **`(userId, orderId, transaction_key)` unique 인덱스는 중복 방지 효과가 없다.** `transactionKey`는 매 요청마다 `TransactionKeyGenerator`가 새 UUID로 생성하므로, 같은 `orderId`로 N번 요청하면 유니크 인덱스를 통과하는 **별도의 Payment row N개**가 생성되고, 각각 독립적으로 비동기 처리(승인/실패)된다. 즉 PG 쪽에는 "같은 주문에 대한 멱등성 보장"이 전혀 없다 — commerce-api가 PG를 중복 호출하지 않도록 직접 막아야 한다.

### 요청~처리 전체 흐름 (단계별)

```
[commerce-api]                          [pg-simulator]
      │
      │  POST /api/v1/payments
      │ ─────────────────────────────────────────────────────► PaymentApi.request()
      │                                                              │ ① request.validate() (즉시, 실패 시 400)
      │                                                              │ ② Thread.sleep(100~500ms)  ← Tomcat 워커 스레드 점유
      │                                                              │ ③ 40% 확률 → CoreException(INTERNAL_ERROR) (②를 거친 후 발생)
      │                                                              │ (60% 통과)
      │                                                              │ ④ createTransaction() [@Transactional]
      │                                                              │    - transactionKey 생성
      │                                                              │    - Payment 저장 (status: PENDING)
      │                                                              │    - PaymentEvent.PaymentCreated 발행(트랜잭션 내 큐잉)
      │  ◄──────── { transactionKey, status: PENDING } ────────────┤    - 메서드 리턴 → 트랜잭션 커밋
      │                                                              │
      │                                                              │ ⑤ [AFTER_COMMIT] @Async PaymentEventListener.handle(PaymentCreated)
      │                                                              │    - 풀의 별도 스레드에서 실행
      │                                                              │    - Thread.sleep(1~5s)
      │                                                              │    - paymentApplicationService.handle(transactionKey)
      │                                                              │
      │                                                              │ ⑥ handle() [@Transactional] : rate = (1..100).random()
      │                                                              │    1~20   → limitExceeded()
      │                                                              │    21~30  → invalidCard()
      │                                                              │    31~100 → approve()
      │                                                              │    PaymentEvent.PaymentHandled 발행 → 트랜잭션 커밋
      │                                                              │
      │                                                              │ ⑦ [AFTER_COMMIT] @Async PaymentEventListener.handle(PaymentHandled)
      │                                                              │    - notifyTransactionResult() (비@Transactional, 단순 조회+호출)
      │  POST {callbackUrl}                                          │
      │ ◄──────────────────────────────────────────────── ⑧ PaymentCoreRelay.notify() — RestTemplate.postForEntity()
```

**눈에 띄는 디테일**

- ②의 `Thread.sleep`은 검증(①) **이후**, 실패 확률 판정(③) **이전**에 실행된다 → 40% 실패 케이스도 100~500ms 지연을 동일하게 물고 간다. "빠르게 실패"하는 경로가 없다.
- ⑤와 ⑦은 각각 별개의 `@Async` 호출이라 풀의 다른 스레드가 잡을 수도, 같은 스레드가 잡을 수도 있다. 커스텀 `Executor` 설정이 없으므로 Spring Boot 기본값(core 8, 무제한 큐)을 그대로 쓰는데, ⑤ 단계에서 스레드 하나가 `Thread.sleep(1~5s)`로 통째로 점유되므로, 동시에 들어오는 결제 건이 8개를 넘으면 **뒤의 건은 큐에서 대기하다가 "1~5초"보다 더 늦게 처리될 수 있다** (테스트 환경에서 동시 결제 수가 많아지면 문서상 스펙(1~5s)보다 실제 지연이 커질 수 있다는 뜻).
- ⑥의 비율 판정은 매 호출마다 새로 `(1..100).random()`을 굴리는 것이라, 같은 PG 인스턴스라도 같은 카드/같은 사용자라고 결과가 보장되지 않는다 (재현 불가능한 랜덤 — 테스트에서 특정 결과를 강제하려면 mock/stub이 필요하다는 의미).

### 콜백(Relay) 전송 방식

```kotlin
// PaymentCoreRelay.kt — 싱글턴 RestTemplate (커넥션/타임아웃 설정 없음)
runCatching {
    restTemplate.postForEntity(callbackUrl, transactionInfo, Any::class.java)
}.onFailure { e -> logger.error("콜백 호출을 실패했습니다. {}", e.message, e) }
```

- `notifyTransactionResult()` 자체는 `@Transactional`이 아니다 — Payment를 다시 조회만 하고 쓰기는 없다.
- `RestTemplate()` 기본 생성자는 connect/read 타임아웃을 설정하지 않는다 → 상대(commerce-api)가 커넥션만 열어두고 응답하지 않으면 OS 소켓 타임아웃까지 무한정 대기할 수 있다.
- 실패는 로그만 남기고 끝 (`runCatching`으로 삼킴) — 재시도 없음, 콜백 실패가 PG 쪽 Payment 상태에 영향을 주지 않음.
- 즉 "콜백 전송 성공 여부"를 PG는 전혀 추적하지 않는다 — 콜백이 commerce-api에 도달했는지 확인할 방법은 PG의 조회 API(`GET /api/v1/payments/{transactionKey}`)로 상태를 다시 묻는 것뿐이다.

**콜백으로 전달되는 데이터 (TransactionInfo)**

```json
{
  "transactionKey": "20250816:TR:9577c5",
  "orderId": "1351039135",
  "status": "SUCCESS",
  "reason": "정상 승인되었습니다.",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000
}
```

### transactionKey 생성 규칙

```kotlin
// TransactionKeyGenerator.kt
"${yyyyMMdd}:TR:${UUID.randomUUID().toString().replace("-", "").substring(0, 6)}"
// 예: "20260622:TR:9577c5"
```

- 날짜는 일 단위까지만 들어가고, 충돌 방지는 UUID 앞 6자리(16^6 ≈ 1,677만 조합)에 의존한다. 하루에 같은 PG 인스턴스에서 결제량이 매우 많아지면 충돌 가능성이 이론적으로 존재하지만, `transactionKey`가 `@Id`라 충돌 시 `save()`에서 PK 제약 위반으로 즉시 실패한다 (별도 재시도 로직 없음).

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
```

**왜 위험한가 — 구체적으로 3가지**

| 문제 | 내용 |
|------|------|
| 커넥션 점유 | 커밋은 PG 응답이 와야 일어나므로, PG가 느릴수록 DB 커넥션을 그만큼 오래 쥐고 있음. 동시 요청이 늘면 커넥션 풀 고갈로 이어짐 |
| 롤백 불일치 | PG 호출까지는 성공했는데 트랜잭션 뒤쪽 코드에서 예외가 나 롤백되면, DB는 깨끗이 되돌아가지만 PG엔 이미 요청이 들어가 있음. **외부 호출은 롤백이 안 되므로** 내부(롤백됨)와 외부(PG는 처리 중) 상태가 영구히 어긋남 |
| 락 보유 시간 증가 | `order.startPayment()`로 잡은 row 락을 PG 응답을 기다리는 동안 계속 쥐고 있음 → 같은 주문에 접근하는 다른 트랜잭션의 대기 시간이 PG 응답 속도에 종속됨 |

요컨대 **외부 시스템은 트랜잭션의 ACID 보장 범위 밖에 있는데, 그걸 트랜잭션 안에 억지로 넣으면 트랜잭션의 원자성(전부 성공 또는 전부 롤백)이 거짓이 된다.**

**"트랜잭션을 나눈다"는 것의 의미**

PG와 commerce-api는 별도 프로세스·별도 DB라서 처음부터 트랜잭션을 공유할 방법이 없다 — 이건 자명한 전제고 우리가 결정할 사항이 아니다.

여기서 "나눈다"는 건 **commerce-api 자신의 트랜잭션 경계를 PG 호출 전/후로 쪼개는 것**을 뜻한다. PG 호출은 어느 트랜잭션에도 속하지 않은 채(트랜잭션 밖에서) 실행되고, 우리 쪽 DB 쓰기만 트랜잭션으로 감싼다:

```
@Transactional
startPayment() { order.status = IN_PAYMENT }   // 트랜잭션 A, 커밋

pgClient.requestPayment()                       // 트랜잭션 밖

@Transactional
handleCallback() { order.confirm() }            // 트랜잭션 B, 커밋 (콜백 수신 시)
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
