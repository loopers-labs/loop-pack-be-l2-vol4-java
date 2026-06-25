# 07. 외부 시스템 연동 분석 — PG 연동 트레이드오프 & 결정 기록

> PG 연동 설계 과정에서 마주한 불확실성, 트레이드오프, 결정 사항을 기록합니다.
> "왜 이렇게 했지?"를 나중에 떠올릴 때 참조하세요.

---

## 배경

기존 commerce-api 결제 흐름은 PG 호출이 전혀 없었다.

```
POST /orders/{id}/pay/start   → order.startPayment() → status: IN_PAYMENT  (PG 호출 없음)
POST /orders/{id}/pay/confirm → order.confirm()      → status: CONFIRMED   (PG 호출 없음)
```

이번 작업의 출발점은 **"PG를 어디서, 어떻게 호출하고, 결과를 어떻게 반영할 것인가"**다.

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

**검증 규칙 (`PaymentDto.PaymentRequest.validate()`, 지연/실패 확률 적용 전에 즉시 실행)**

| 필드 | 규칙 | 위반 시 |
|------|------|---------|
| `orderId` | blank 아니고 길이 ≥ 6 | `BAD_REQUEST` |
| `cardNo` | 정규식 `^\d{4}-\d{4}-\d{4}-\d{4}$` | `BAD_REQUEST` |
| `amount` | > 0 | `BAD_REQUEST` |
| `callbackUrl` | `http://localhost:8080` 로 시작 | `BAD_REQUEST` |

> **주의:** `callbackUrl`은 `http://localhost:8080` prefix가 하드코딩 검증돼 있어, commerce-api 외 다른 호스트/포트로는 콜백을 보낼 수 없다.

### Payment 엔티티 — 상태 전이

```
PENDING ──── approve() ──────► SUCCESS  (reason: "정상 승인되었습니다.")
        └─── limitExceeded() ─► FAILED  (reason: "한도초과입니다. 다른 카드를 선택해주세요.")
        └─── invalidCard() ───► FAILED  (reason: "잘못된 카드입니다. 다른 카드를 선택해주세요.")
```

**`(userId, orderId, transaction_key)` unique 인덱스는 중복 방지 효과가 없다.** `transactionKey`는 매 요청마다 새 UUID로 생성되므로, 같은 `orderId`로 N번 요청하면 별도의 Payment row N개가 생성되고 각각 독립적으로 처리된다. PG 쪽에는 "같은 주문에 대한 멱등성 보장"이 전혀 없다 — commerce-api가 PG를 중복 호출하지 않도록 직접 막아야 한다. (→ 케이스 E, 비관적 락으로 해결)

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
      │                                                              │    - Payment 저장 (status: PENDING)
      │  ◄──────── { transactionKey, status: PENDING } ────────────┤    - 메서드 리턴 → 트랜잭션 커밋
      │                                                              │
      │                                                              │ ⑤ [AFTER_COMMIT] @Async — Thread.sleep(1~5s) 후 처리
      │                                                              │ ⑥ rate = (1..100).random() : 1~20 한도초과 / 21~30 잘못된카드 / 31~100 승인
      │                                                              │ ⑦ [AFTER_COMMIT] @Async — 콜백 전송
      │  POST {callbackUrl}                                          │
      │ ◄──────────────────────────────────────────────── ⑧ PaymentCoreRelay.notify() — RestTemplate.postForEntity()
```

**눈에 띄는 디테일**

- ②의 지연은 검증(①) 이후, 실패 판정(③) 이전에 일어난다 → 40% 실패 케이스도 100~500ms 지연을 동일하게 물고 간다. "빠르게 실패"하는 경로가 없다.
- ⑤ 단계에서 스레드 하나가 `Thread.sleep(1~5s)`로 통째로 점유된다. 커스텀 `Executor` 설정이 없어 스프링 기본 스레드풀(core 8)을 쓰는데, 동시 결제 건이 8개를 넘으면 뒤의 건은 큐에서 대기하다 "1~5초"보다 더 늦게 처리될 수 있다.

### 콜백(Relay) 전송 방식

```kotlin
// PaymentCoreRelay.kt — 싱글턴 RestTemplate (커넥션/타임아웃 설정 없음)
runCatching {
    restTemplate.postForEntity(callbackUrl, transactionInfo, Any::class.java)
}.onFailure { e -> logger.error("콜백 호출을 실패했습니다. {}", e.message, e) }
```

- `RestTemplate()` 기본 생성자는 connect/read 타임아웃을 설정하지 않는다 → commerce-api가 응답하지 않으면 OS 소켓 타임아웃까지 무한정 대기할 수 있다.
- 실패는 로그만 남기고 끝난다 (재시도 없음). PG는 "콜백이 commerce-api에 도달했는지"를 전혀 추적하지 않는다 — 확인할 방법은 PG의 조회 API로 상태를 다시 묻는 것뿐이다.
- 콜백에 서명이나 인증 헤더가 전혀 없다 — commerce-api가 콜백의 발신지를 검증할 방법이 없다는 뜻이다. (→ 결정 6에서 이 문제를 다룬다)

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

---

## 왜 트랜잭션을 합치면 안 되는가

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

| 문제 | 내용 |
|------|------|
| 커넥션 점유 | PG가 느릴수록 DB 커넥션을 그만큼 오래 쥐고 있음. 동시 요청이 늘면 커넥션 풀 고갈로 이어짐 |
| 롤백 불일치 | PG 호출은 성공했는데 트랜잭션 뒤쪽에서 예외가 나 롤백되면, DB는 되돌아가지만 PG엔 이미 요청이 들어가 있음. 외부 호출은 롤백이 안 되므로 내부와 외부 상태가 영구히 어긋남 |
| 락 보유 시간 증가 | `order.startPayment()`로 잡은 row 락을 PG 응답을 기다리는 동안 계속 쥐고 있음 → 같은 주문에 접근하는 다른 트랜잭션이 PG 응답 속도에 종속됨 |

요컨대 **외부 시스템은 트랜잭션의 ACID 보장 범위 밖에 있는데, 그걸 트랜잭션 안에 억지로 넣으면 트랜잭션의 원자성이 거짓이 된다.**

여기서 "트랜잭션을 나눈다"는 건 **commerce-api 자신의 트랜잭션 경계를 PG 호출 전/후로 쪼개는 것**을 뜻한다 (PG와 commerce-api는 원래부터 트랜잭션을 공유할 방법이 없으니, 이건 자명한 전제다):

```
@Transactional
startPayment() { order.status = IN_PAYMENT }   // 트랜잭션 A, 커밋

pgClient.requestPayment()                       // 트랜잭션 밖

@Transactional
handleCallback() { order.confirm() }            // 트랜잭션 B, 커밋 (콜백 수신 시)
```

이렇게 나누는 게 옳을지, 아니면 위험을 감수하고 합칠지는 구현하면서 다시 따져본다 (→ "구조 선택의 트레이드오프" 절).

---

## 콜백 중복 수신 문제

PG는 같은 결과를 두 번 보낼 수 있다 (재시도 없는 단발성 호출이지만, 네트워크 상황에 따라 commerce-api가 같은 콜백을 중복으로 받는 경우는 배제할 수 없다).

```
콜백이 두 번 오면?
→ order.confirm()이 두 번 호출됨
→ OrderModel.confirm()에는 상태 검증이 없으면 이미 CONFIRMED인 주문에 또 confirm() 가능
```

`confirm()`/`fail()` 호출 전 **이미 처리된 상태면 무시하는 방어 로직**이 필요하다. (Must-Have로 반영)

---

## 이번 주 구현 우선순위

### Must-Have

| 항목 | 이유 |
|------|------|
| Timeout 설정 | PG 지연이 내부 스레드를 점유하지 않도록 |
| CircuitBreaker | PG 장애가 commerce-api 전체로 번지지 않도록 |
| Fallback 응답 | PG가 막혀있어도 사용자에게 즉시 응답 |
| 콜백 수신 엔드포인트 | 비동기 결제이므로 결과를 받을 창구가 필수 |
| 콜백 중복 처리 방어 | 콜백이 두 번 오면 confirm()이 두 번 호출됨 |

### Nice-To-Have

| 항목 | 이유 |
|------|------|
| Retryer | PG 요청 실패 시 재시도 (멱등성 전제 필요) → 결정 11에서 멱등키 구현 완료 |
| 상태 복구 API or 스케줄러 | 콜백 미수신 건을 PG 조회로 보정 |

---

## 구현 전 결정 사항

### 결정 1. PG 클라이언트 기술 — FeignClient
- Must-Have인 CircuitBreaker + Timeout + Fallback 세 개를 yml 설정만으로 처리 가능
- RestTemplate 대비 Resilience4j 연동 코드량이 적음
- 에러 처리는 `ErrorDecoder`로 별도 구현 필요 → 실제로는 미뤄지다가 결정 6에서야 구현했다

### 결정 2. PAYMENT_FAILED 상태 추가
- 콜백으로 FAILED 수신 시 Order 상태를 `PAYMENT_FAILED`로 전이
- `OrderStatus` enum에 추가

### 결정 3. 콜백 엔드포인트 경로
- `POST /api/v1/payments/callback`

---

## 구현 후 분석 — 실패 케이스 5가지 (A~E)

구현 전에는 "PG 호출이 실패하면 Order를 어떤 상태로 보낼지"가 정해지지 않았었다. 초기 구현(단일 `@Transactional`)에서는 PG 호출이 실패하면 트랜잭션 전체가 롤백되어 Order가 자동으로 PENDING_PAYMENT로 돌아갔다. 그러나 이후 k6 테스트 결과 커넥션 풀 포화가 확인되어 **트랜잭션 경계를 분리하는 방향(선택지 2)으로 전환**했다 (→ "개선 적용 및 재측정" 절). 분리 후에는 TX1이 PG 호출 전에 커밋되므로, PG 실패 시 롤백이 아니라 명시적 TX C(보정 트랜잭션)로 Order가 PAYMENT_FAILED로 전이된다. 아래 케이스들은 **현재 tx-split 구조 기준**으로 동작을 설명한다.

#### 케이스 A — PG 즉시 에러 (40% 확률)

```
[TX1] order.startPayment() → IN_PAYMENT → 커밋 → 커넥션 반환
[PG 호출] pgPaymentClient.request() → 100~500ms 후 INTERNAL_ERROR 응답 → 예외 발생
[TX C] order.failPayment() → PAYMENT_FAILED → 커밋
```

PG에 결제 건이 생성되지 않았으므로 내부/외부 상태 불일치 없음. `startPayment()`가 PAYMENT_FAILED에서도 재시도를 허용하므로 사용자는 재결제 가능.

#### 케이스 B — PG 응답이 600ms를 넘게 걸림 (TimeLimiter 타임아웃)

1. TX1이 커밋된다 (Order: IN_PAYMENT).
2. PG 호출 시작. 600ms 안에 응답이 안 온다 → TimeLimiter가 타임아웃 예외를 던진다.
3. TX1은 이미 커밋됐으므로 롤백 불가. catch 블록 → **TX C → Order: PAYMENT_FAILED**.
4. **그런데 PG는 그 요청을 이미 받아서 처리하고 있을 수 있다.** commerce-api가 기다리길 포기한 것뿐이지, PG 입장에서는 정상 진행 중인 결제다.
5. PG가 처리를 끝내고 콜백을 보낸다. commerce-api에는 이 거래의 `PaymentModel`이 없어서(TX2가 실행되기 전에 예외가 발생했으므로) **기존 콜백 처리 코드는 "결제 정보 없음"으로 무시했다.** 이는 버그였다.

Order가 PAYMENT_FAILED이므로 사용자가 재결제할 수 있다. 다만 재결제하면 PG에는 같은 주문에 대한 거래가 2개 생기게 된다 — 실제 서비스라면 첫 번째 건에 환불 처리가 필요하지만 이 과제 범위 밖이다.

→ 복구 API 범위는 **결정 6에서 orderId 기반 조회로 확장**했다. 콜백 경로 자체의 버그는 **결정 9에서 수정**했다.

#### 케이스 C — CircuitBreaker OPEN

```
PG 실패율 60% 초과 → CircuitBreaker OPEN
TX1 커밋 (Order: IN_PAYMENT)
→ pgPaymentClient.request() → Fallback 즉시 발동 (PG 호출 자체가 일어나지 않음)
→ Fallback이 예외 던짐 → catch 블록 → TX C → Order: PAYMENT_FAILED
```

PG 호출이 아예 일어나지 않아 PG 쪽에 고아 거래가 생길 일이 없는 가장 깔끔한 실패 케이스.

**설정 버그로 한동안 동작 안 함 → 수정.** `spring.cloud.openfeign.circuitbreaker.enabled: true`가 local/test 프로파일에만 선언되어 있어 dev/qa/prd에서는 CB와 Fallback이 동작하지 않았다. 전역 `spring:` 블록으로 옮겨 모든 프로파일에 적용했다.

#### 케이스 D — PG 요청 성공, 내부 저장 실패

```
[TX1] order.startPayment() → IN_PAYMENT → 커밋
[PG 호출] pgPaymentClient.request() → 성공, transactionKey 반환
[TX2] paymentRepository.save() → DB 일시 장애 등으로 실패 → TX2 롤백
```

TX2 실패는 PG 호출 성공 이후이므로 catch 블록(TX C)이 실행되지 않는다. Order는 IN_PAYMENT에 머무르고 PaymentModel은 로컬에 없는 상태다. 케이스 B와 동일하게 **결정 6(orderId 기반 PG 재조회)으로 해결.** 복구 API 호출 시 PG에서 거래를 찾아 PaymentModel을 새로 동기화하고 Order 상태를 보정한다.

#### 케이스 E — 동시 결제 요청 (Race Condition)

```
[락 없는 경우]
TX1: SELECT orders WHERE id=? → PENDING_PAYMENT (읽음)
TX2: SELECT orders WHERE id=? → PENDING_PAYMENT (TX1 커밋 전, 동일 스냅샷)
TX1, TX2 둘 다 startPayment() 메모리 체크 통과 → 둘 다 PG 호출 성공 → 둘 다 커밋

결과: PG에 결제 건 2개, PaymentModel 2개 생성
```

`order.startPayment()`의 상태 체크는 메모리 레벨 검증이라, 두 트랜잭션이 같은 상태를 읽고 시작하면 둘 다 통과한다.

**비관적 락으로 해결.** `OrderJpaRepository`에 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 메서드를 추가하고, 결제 요청(`requestPayment`)에서만 이 메서드를 쓰도록 했다.

```
[비관적 락 적용 후]
TX1: SELECT ... FOR UPDATE → 락 획득 → startPayment() → PG 호출 → save → 커밋 → 락 해제
TX2: 락 획득 대기 → (TX1 커밋 후) Order 읽음 → status: IN_PAYMENT → startPayment() BAD_REQUEST → 종료

결과: PG 호출 한 번, PaymentModel 하나
```

---

## 구조 선택의 트레이드오프 — 트랜잭션을 합칠까, 나눌까

### 검토한 선택지

**선택지 1: 현재 트랜잭션 구조 유지 + 비관적 락 + 복구 API**

```
@Transactional {
    order.startPayment()      ← SELECT FOR UPDATE로 락 보유
    pgPaymentClient.request() ← 락 보유 중 외부 호출 (최대 600ms)
    paymentRepository.save()
}
```

비관적 락이 PG 호출 중 DB 커넥션을 점유하는 단점이 있지만, TimeLimiter 600ms가 상한선이라 락 보유 시간도 최대 600ms로 제한된다. PG 실패 시 자동 롤백으로 Order가 PENDING_PAYMENT로 복원된다.

**선택지 2: 트랜잭션 경계 분리 + 명시적 복구 처리**

```
[TX A] order.startPayment() → 커밋 (락 해제)
[트랜잭션 밖] pgPaymentClient.request()
    성공 → [TX B] paymentRepository.save()
    실패 → [TX C] order.revertToPendingPayment() ← 명시적 처리
```

락 보유 시간을 최소화하고 케이스 E를 구조적으로 해결하지만, PG 실패 시 Order가 IN_PAYMENT에 고착되므로 TX C가 필요하고, TX C 자체가 실패하면 Order가 영구 고착된다. 케이스 B/D(PG가 실제로 처리했는지 알 수 없는 경우)는 이 구조에서도 여전히 복구 API가 필요하다.

### 초기 결정 — 선택지 1 유지 (k6 테스트 전)

선택지 2는 케이스 E 하나를 구조적으로 해결하기 위해 트랜잭션 경계를 전부 재설계하지만, B/D는 어느 선택지에서도 복구 API 없이는 해결되지 않는다. 추가 복잡도 대비 실질적 이득이 없다고 판단했다.

| 케이스 | 해결 방법 |
|--------|-----------|
| A. PG 즉시 에러 | TX C → PAYMENT_FAILED, 사용자 재결제 가능 |
| B. TimeLimiter 초과 | TX C → PAYMENT_FAILED + 결정 6 (orderId 기반 PG 재조회) |
| C. CircuitBreaker OPEN | TX C → PAYMENT_FAILED + application.yml 프로파일 버그 수정 |
| D. 내부 저장 실패 | Order 상태 IN_PAYMENT 유지 + 결정 6 (B와 동일) |
| E. 동시 요청 | 비관적 락 |

### 그런데 이 선택, 정말 안전한가? — 재검토

"왜 트랜잭션을 합치면 안 되는가"에서 외부 호출을 트랜잭션 안에 넣는 게 위험하다고 지적했는데, 결국 똑같은 패턴(락 보유 중 PG 호출)을 선택지 1로 택했다. 이건 "위험을 없앤 게" 아니라 "위험의 크기를 600ms로 캡핑한 것"이다. 그 캡핑이 실제로 유효한지 다시 확인했다.

**확인 1. 캡핑의 근거였던 600ms 설정이 dev/qa/prd에는 적용되지 않고 있었다.**

`resilience4j.circuitbreaker.instances.pg-simulator`, `resilience4j.timelimiter.instances.pg-simulator`(600ms) 설정이 `local, test` 프로파일에만 있었다. 케이스 C에서 고친 `spring.cloud.openfeign.circuitbreaker.enabled`는 전역으로 옮겼지만, 실제 임계치·타임아웃 값은 local/test 전용으로 남아 있었다 — 같은 종류의 프로파일 스코프 버그가 한 곳 더 있었던 것. dev/qa/prd에서는 Resilience4j 기본값(TimeLimiter 1s)으로 동작해 "락은 최대 600ms"라는 전제가 실제로는 성립하지 않았다.

→ **수정 완료.** `resilience4j:` 블록을 전역 `spring:` 블록으로 이동했다. `pg.url`/`callback-url`은 환경별로 값이 달라야 하므로 `local, test` 블록에 그대로 둔다.

**확인 2. 커넥션 점유 위험의 실제 크기.**

"락 대기"와 "커넥션 점유"는 서로 다른 문제다.

| 위험 | 영향 범위 | 평가 |
|------|----------|------|
| 락 대기 | **같은 주문**에 대한 동시 요청만 차단 | 케이스 E를 막기 위한 의도된 동작 — 문제 아님 |
| 커넥션 점유 | **모든** 결제 요청이 PG 응답까지 커넥션 1개씩 점유 | `maximum-pool-size: 40`(`modules/jpa/src/main/resources/jpa.yml:22`) 기준, 600ms 안에 **동시 40건 이상**이 겹쳐야 풀이 고갈됨 — 현재 트래픽 규모에서는 가능성이 낮음 |

**초기 결론 (k6 테스트 전).** 600ms 캡이 모든 프로파일에 실제로 적용되고, 동시 결제량이 풀 크기(40) 이하인 한 선택지 1의 위험은 감당 가능한 수준이다. 선택지 2는 점유 시간을 0으로 줄이지만 B/D의 복구 필요성은 그대로 남고 새로운 실패 케이스(TX C 실패 시 영구 고착)가 추가되므로, 이 시점에서는 추가 복잡도를 들일 이유가 없다고 판단했다.

**최종 결정 — 선택지 2로 전환 (k6 테스트 후).** 그러나 k6 부하 테스트(80 VU)에서 `pool_timeout_rate` 7.02%가 실측됐다. "동시 결제량이 풀 크기(40) 이하인 한"이라는 전제가 실제 부하에서는 성립하지 않음을 수치로 확인한 것이다. 커넥션 점유 문제가 이론이 아니라 현실적 규모에서도 발생하므로, 선택지 2의 추가 복잡도가 정당화된다고 판단하고 **선택지 2로 전환**했다 (→ "개선 적용 및 재측정" 절).

---

## 구현 후 결정 사항

### 결정 4. 비관적 락 적용 범위 (초기)
- 결제 요청(`requestPayment`)에서만 `SELECT FOR UPDATE`를 사용한다.
- 이유: 결제 요청만이 동시 접근으로 인한 중복 PG 호출을 유발할 수 있는 경로이기 때문이다.
- 이후 복구 API(`recoverPayment`)에서도 동시 복구 요청 시 PaymentModel 중복 생성 문제가 발견되어 비관적 락이 추가됐다 (→ 결정 10).

### 결정 5. 복구 API — POST /api/v1/payments/{orderId}/recover
- 콜백 미수신으로 IN_PAYMENT에 고착된 결제 건을 수동으로 복구한다.
- `orderId`로 `PaymentModel`(PENDING 상태)을 찾고, PG `GET /api/v1/payments/{transactionKey}`로 실제 상태를 조회해 내부 상태를 보정한다.
- PG가 아직 PENDING을 반환하면 상태를 변경하지 않는다 (아직 처리 중).
- 이미 SUCCESS/FAILED 처리된 건은 멱등하게 무시한다.

### 결정 6. 복구 API 확장 — orderId 기반으로도 PG에 물어볼 수 있게 함

체크리스트가 "타임아웃으로 실패한 결제건도 정보를 확인해서 반영해야 한다"고 명시적으로 요구한다. 그런데 케이스 B/D는 `PaymentModel` 자체가 로컬에 없어서(트랜잭션이 롤백됨), 결정 5의 복구 API(`transactionKey` 기준 조회)로는 손을 댈 수 없었다.

**해결책.** PG에는 `transactionKey`를 몰라도 `orderId`만으로 거래를 찾는 API(`GET /api/v1/payments?orderId=`)가 있다. 로컬에 Payment가 없을 때 이 API로 먼저 물어보고, 있으면 그 거래의 상세를 다시 조회해서 로컬에 동기화하도록 `recoverPayment()`를 확장했다.

```
로컬에 Payment 없음
  → PG에 orderId로 조회
    → PG에도 없음(404)        : 복구할 것 없음, 종료
    → PG에 거래 있음(최신 1건) : 그 transactionKey로 단건 재조회 → PaymentModel을 새로 만들어 동기화
```

**구현하다가 발견한 문제 — Fallback이 PG의 정상 응답까지 가려버림.**

이 기능은 "PG가 404(없음)라고 답하면 복구할 게 없다"는 판단이 핵심이다. 그런데 Spring의 서킷브레이커 연동 방식 때문에, PG 호출에서 예외가 나면 종류를 가리지 않고 무조건 대체 응답(Fallback)으로 빠진다. 기존 `PgPaymentFallback`은 원인을 보지 않고 항상 "서비스 불가"만 던지므로, PG가 정상적으로 보낸 404(없음)까지 "장애"로 둔갑해버려 위 분기를 구현할 수 없었다. 두 가지를 고쳤다.

1. **`PgErrorDecoder` 추가** — PG가 보낸 HTTP 상태 코드(404, 400 등)를 우리 쪽 에러 타입(`CoreException`)으로 정확히 변환한다. (결정 1에서 하기로 했지만 실제로는 안 만들어져 있던 부분)
2. **`PgPaymentFallback`(무조건 대체 응답) → `PgPaymentFallbackFactory`(원인을 보고 판단)로 교체** — PG가 실제로 응답한 에러(404, 400 등)는 그대로 전달하고, 진짜로 응답을 못 받았을 때(타임아웃, 서킷 OPEN)만 "서비스 불가"로 대체한다.

**한계.** PG의 주문 기준 조회는 카드 종류·금액 정보를 안 줘서, 새로 만드는 결제 기록의 카드 종류는 `"UNKNOWN"`으로, 금액은 우리가 PG에 요청했던 금액(`Order.finalAmount`)으로 채운다. 둘 다 실제 로직에는 쓰이지 않는 부수 정보라 문제는 없다.

**남은 일 (미구현).** 서킷브레이커의 실패율 집계는 이 원인 구분과 무관하게 404/400도 그냥 "실패"로 센다. 정상적인 "없음" 응답이 잦으면 서킷브레이커가 불필요하게 열릴 수 있다는 뜻이다. `resilience4j.circuitbreaker.instances.pg-simulator.ignore-exceptions`로 비즈니스성 예외를 통계에서 빼는 게 다음 단계지만, 현재 규모에서는 우선순위가 낮아 범위 밖으로 남긴다.

### 결정 7. PAYMENT_FAILED 주문 재결제 허용

처음엔 `OrderModel.startPayment()`가 `PENDING_PAYMENT`에서만 결제를 시작할 수 있어서, 한 번 실패(PAYMENT_FAILED)한 주문은 재결제할 방법이 없었다. 이걸 열어주기로 했다.

```java
if (this.status != OrderStatus.PENDING_PAYMENT && this.status != OrderStatus.PAYMENT_FAILED) {
    throw new CoreException(ErrorType.BAD_REQUEST, "결제를 시작할 수 없는 주문 상태입니다.");
}
```

**실패 사유별로 다르게 처리하지 않는 이유.** 실제 카드 결제망에는 두 종류의 실패가 있다.
- hard decline (한도초과, 잘못된 카드) — 같은 카드로 다시 시도해도 똑같이 거절된다.
- soft decline (카드사 시스템 점검 등 일시적 장애) — 시간을 두고 다시 시도하면 성공할 수 있다.

실무 PG는 이 둘을 구분해서 soft decline만 자동으로 재시도하는 경우가 많다. 하지만 이 구분은 PG가 실패 사유를 코드로 알려줄 때만 가능한데, pg-simulator는 "한도초과"/"잘못된 카드"(둘 다 hard decline)만 주고 soft decline에 해당하는 사유가 없다. 시뮬레이터가 절대 만들지 않는 경우를 처리하는 코드는 검증할 수 없으니 만들지 않는다.

대신 시스템은 실패 사유를 따지지 않고 "재결제 가능" 상태로만 열어주고, 같은 카드를 쓸지 다른 카드를 쓸지는 사용자가 결정한다.

> 참고: 실제 PG가 soft decline을 구분해서 알려준다면, 그 경우만 스케줄러로 자동 재시도하는 게 다음 단계가 된다. 지금은 범위 밖.

### 결정 8. 외부 클라이언트 타임아웃 명시적 설정 — Feign / Redis

TimeLimiter가 있어서 안전하다고 생각했지만, 클라이언트 레벨 타임아웃이 빠져 있으면 TimeLimiter가 스레드를 인터럽트해도 소켓은 여전히 대기 중인 채로 남을 수 있다. 각 클라이언트마다 독립적인 타임아웃을 추가했다.

**Feign — connectTimeout / readTimeout 분리**

HTTP 호출은 두 단계로 나뉜다.

| 단계 | 타임아웃 | 걸리는 시점 | 설정값 |
|------|---------|------------|--------|
| TCP 연결 수립 | connectTimeout | PG 서버가 죽어있거나 네트워크가 막혔을 때 | 1,000ms |
| 응답 대기 | readTimeout | 연결은 됐는데 PG가 응답을 보내지 않을 때 | 3,000ms |

readTimeout을 3,000ms로 둔 이유: TimeLimiter(600ms)가 정상 경로에서는 먼저 끊어주지만, 인터럽트가 소켓 레벨까지 내려가지 않을 경우를 대비한 백스톱이다. TimeLimiter가 끊은 이후에도 소켓이 열려 있으면 최대 3초 뒤 Feign이 강제로 닫는다.

```yaml
# application.yml
spring.cloud.openfeign.client.config.default:
  connectTimeout: 1000
  readTimeout: 3000
```

**Redis — Lettuce commandTimeout**

`redis.yml`에는 `spring.data.redis.timeout` 대신 `RedisConfig`에서 `LettuceClientConfiguration`을 직접 빌드하는 구조라, yml 속성이 자동으로 반영되지 않는다. 코드에서 직접 설정해야 한다.

설정 전에는 Redis 명령이 응답을 영원히 기다릴 수 있었다. Redis는 응답이 빠른 인메모리 저장소이므로, 500ms 안에 오지 않으면 장애로 판단하고 즉시 실패하는 게 맞다.

```java
// RedisConfig.java
LettuceClientConfiguration.builder()
    .commandTimeout(Duration.ofMillis(500))
    ...
```

**JPA (HikariCP) — connection-timeout은 이미 설정되어 있었다**

```yaml
# jpa.yml
connection-timeout: 3000  # 커넥션 풀에서 대기하는 최대 시간
```

**세 설정의 역할 요약**

| 클라이언트 | 타임아웃 | 없으면 |
|-----------|---------|-------|
| Feign connectTimeout | 1,000ms | PG가 다운됐을 때 무기한 대기 |
| Feign readTimeout | 3,000ms | TimeLimiter 인터럽트 후 소켓 좀비 |
| Redis commandTimeout | 500ms | Redis 장애 시 요청 스레드 무기한 점유 |
| HikariCP connection-timeout | 3,000ms | 커넥션 풀 고갈 시 요청 무기한 대기 |

### 결정 9. 타임아웃 케이스 콜백 버그 수정 — `handleCallback`에 orderId 추가

타임아웃으로 PaymentModel이 로컬에 없는 상태에서 PG 콜백이 도착하면 `findByTransactionKey`가 빈 Optional을 반환하고, 기존 코드는 여기서 NOT_FOUND 예외를 던져 콜백을 조용히 버렸다. PG에서는 결제가 성공했지만 Order는 PAYMENT_FAILED로 남는 버그다.

```
[수정 전]
handleCallback(transactionKey)
  → paymentRepository.findByTransactionKey(transactionKey)
  → 없으면 throw NOT_FOUND ← 콜백 버려짐

[수정 후]
handleCallback(transactionKey, orderId)
  → paymentRepository.findByTransactionKey(transactionKey)
  → 없으면 → orderRepository.find(orderId) → Order 조회
           → pgClient.getTransaction(order.loginId, transactionKey)
           → PaymentModel 신규 생성 + 상태 보정
```

수정 내용:

1. `CallbackRequest`에 `orderId` 필드 추가. pg-simulator는 원래부터 콜백 바디에 `orderId`를 포함해 전송하고 있었다 — 역직렬화 대상에서 빠져 있었을 뿐이다.
2. `handleCallback(transactionKey, orderId)` — PaymentModel이 없을 때 orderId로 Order를 찾아 `recoverFromPgByTransactionKey` 호출.
3. `recoverFromPgByTransactionKey`: 이미 알고 있는 `transactionKey`로 PG에 바로 재조회 후 PaymentModel 신규 생성 + 상태 보정. `recoverFromPgByOrderId`(결정 6)와 달리 orderId 기반 목록 조회를 거치지 않아 PG 호출 1회로 줄어든다.
4. **OrderModel에 `loginId` 추가**: PG 재조회(`pgClient.getTransaction`)에는 `X-USER-ID` 헤더(loginId)가 필요하다. 콜백 경로는 인증 없는 엔드포인트이고 PaymentModel도 없으므로, loginId를 얻을 수 있는 유일한 경로가 OrderModel이다. PaymentModel이 이미 `loginId`를 저장하는 이유와 동일하다 — PG 호출 시 사용자 식별이 필요하기 때문이다.

**orderId 위조 가능성.** orderId를 콜백 바디에서 읽는 것은 신뢰하지 않는 외부 입력을 사용하는 것처럼 보이지만, orderId는 경로 탐색에만 사용하고 실제 상태 반영은 `pgClient.getTransaction(transactionKey)`로 PG에서 직접 가져온 값만 사용한다. orderId를 위조해 콜백을 보내도 얻을 수 있는 것은 "없는 Order에 대한 NOT_FOUND 유도"뿐이어서 실질적 보안 위협이 없다.

### 결정 12. Retry 도입 — 일시적 PG 실패 자동 복구

**횟수 결정 근거**

PG 시뮬레이터의 즉시 실패 확률이 40%이므로, 시도 횟수별 누적 성공 확률은 다음과 같다.

| 총 시도 횟수 | 성공 확률 | 타임아웃 시 최악 응답 시간 |
|-------------|----------|--------------------------|
| 1회 (retry 없음) | 60.0% | 600ms |
| 2회 (retry 1회) | 84.0% | 1,200ms |
| **3회 (retry 2회)** | **93.6%** | **1,800ms** |
| 4회 (retry 3회) | 97.4% | 2,400ms |

3회(retry 2회)를 채택했다. 4회는 성공률이 3.8%p 더 높지만 응답 시간이 600ms 더 길어진다. 결제는 사용자가 대기하는 작업이므로 추가 대기 비용 대비 이득이 크지 않다.

**retry 대상 구분**

모든 실패에 retry하면 안 된다. FallbackFactory가 원인 타입을 보고 세 가지로 분기한다.

```
PG 400/404 (비즈니스 에러)   → CoreException(BAD_REQUEST/NOT_FOUND) 그대로 전달 → retry X
CB OPEN (CallNotPermittedException) → CoreException(SERVICE_UNAVAILABLE)          → retry X
타임아웃 / 네트워크 / PG 500  → PgRetriableException                               → retry O
```

CB OPEN 상태에서는 PG 자체가 다운된 것이므로 retry해도 의미가 없다. 오히려 CB의 half-open 복구 판단을 방해한다.

**대기 시간 200ms 고정**

PG 지연 범위가 100~500ms이므로 200ms 대기 후 재시도하면 일시적 부하가 해소됐을 가능성이 충분하다. 지수 백오프(200 → 400 → 800ms)는 대규모 분산 시스템에서 thundering herd를 방지하기 위한 전략으로, 이 시뮬레이터 규모에서는 과하다.

**CB와의 관계**

Retry는 FallbackFactory 바깥(PaymentFacade)에서 수동으로 구현했다. CB OPEN이면 FallbackFactory에서 `CoreException(SERVICE_UNAVAILABLE)`을 던지므로 `PgRetriableException`이 아니어서 retry 루프를 통과하지 않는다 — CB가 바깥, retry가 안쪽인 구조가 자연스럽게 성립한다.

**최종 실패 처리**

retry 3회를 모두 소진했을 때 `CoreException(SERVICE_UNAVAILABLE)`을 던지며, 기존 TX C(Order PAYMENT_FAILED 전이)가 그대로 실행된다.

### 결정 11. 멱등키(Idempotency Key) 도입 — Retry 전제 조건 확보

**배경.** Nice-To-Have로 분류했던 Retryer를 추가하려면 멱등성이 전제되어야 한다. PG 시뮬레이터는 같은 `orderId`로 N번 요청하면 N개의 독립적인 거래를 생성하므로, retry 시 중복 결제가 발생한다. 이를 해결하기 위해 멱등키를 도입했다.

**멱등키 생성 전략 검토**

| 방식 | 판단 |
|------|------|
| `orderId` 단독 | 재결제(PAYMENT_FAILED → 새 시도) 시 같은 키 → PG가 첫 실패 결과 반환 → 재결제 불가. 탈락 |
| `orderId + 타임스탬프` | retry 시 시간이 달라지면 다른 키 → 멱등 의미 없음. 탈락 |
| `orderId + attempt 번호` | 재결제/retry 구분 가능하지만 attempt 번호를 별도로 관리해야 함 |
| **타임스탬프 + UUID 앞 12자리** | 시간 순 정렬 가능 + 충돌 없음. 채택 |

**채택한 키 형태**

```
20260625-143022-a3f2b4c1d5e6
  yyyyMMdd-HHmmss   UUID 앞 12자리
```

- 타임스탬프: 발행 시각을 키에서 바로 읽을 수 있어 조회/정렬에 유리
- UUID: 같은 초에 여러 요청이 들어와도 충돌 없음
- 이 구조는 UUID v7(타임스탬프 + 랜덤값 조합)과 동일한 개념이다

**생성 위치: `OrderModel.startPayment()`**

`startPayment()`는 결제 시도마다 반드시 한 번 호출되는 진입점이다. 여기서 키를 생성해 `OrderModel`에 저장하면:
- TX1 커밋과 함께 DB에 영속화됨
- PG 호출 시 `order.getIdempotencyKey()`로 꺼내서 사용
- retry 시 같은 Order에서 꺼내므로 동일한 키 재사용 보장
- 재결제(PAYMENT_FAILED) 시 `startPayment()`가 다시 호출되어 새 키 생성 → PG 입장에서 새 요청

**PG 시뮬레이터 변경 (`PaymentApplicationService.createTransaction`)**

```
동일 idempotencyKey 수신
  → DB에 이미 존재 : 기존 Payment 결과 그대로 반환 (새 거래 생성 안 함)
  → 존재하지 않음  : 정상 처리 후 저장
```

**변경 파일 요약**

| 위치 | 변경 내용 |
|------|-----------|
| `Payment.kt` | `idempotencyKey` 필드 추가 (unique 인덱스) |
| `PaymentRepository.kt` / `PaymentJpaRepository.kt` / `PaymentCoreRepository.kt` | `findByIdempotencyKey()` 추가 |
| `PaymentCommand.kt` / `PaymentDto.kt` | `idempotencyKey` 필드 추가 및 검증 |
| `PaymentApplicationService.kt` | 중복 키 수신 시 기존 결과 반환 |
| `OrderModel.java` | `idempotencyKey` 필드, `startPayment()`에서 생성 |
| `PgPaymentClientDto.java` | `PaymentRequest`에 `idempotencyKey` 추가 |
| `PaymentFacade.java` | PG 요청 시 `order.getIdempotencyKey()` 포함 |

### 결정 10. `recoverPayment` 비관적 락 — 동시 복구 요청 시 PaymentModel 중복 생성 방지

복구 API와 콜백이 동시에 들어오면(또는 스케줄러와 수동 호출이 겹치면) 두 요청이 모두 `existingPayment.isEmpty()`를 true로 읽고 PaymentModel을 중복 생성할 수 있다.

```
[수정 전]
요청 A: orderRepository.find(orderId) → IN_PAYMENT 확인 (일반 SELECT)
요청 B: orderRepository.find(orderId) → IN_PAYMENT 확인 (동시, 락 없음)
요청 A: existingPayment.isEmpty() → true → PaymentModel 생성
요청 B: existingPayment.isEmpty() → true → PaymentModel 또 생성 (중복!)

[수정 후]
요청 A: orderRepository.findWithLock(orderId) → SELECT FOR UPDATE, 락 획득
요청 B: orderRepository.findWithLock(orderId) → 락 획득 대기
요청 A: existingPayment.isEmpty() → true → PaymentModel 생성 후 커밋 → 락 해제
요청 B: 락 획득 → existingPayment.isEmpty() → false (이미 생성됨) → 종료
```

**핵심: 락 선점 위치가 isEmpty 체크보다 반드시 앞이어야 한다.** 락을 isEmpty 체크 이후에 잡으면 두 요청이 모두 isEmpty()=true를 확인한 다음 락을 잡으므로 여전히 중복이 발생한다. 락을 먼저 잡아야 첫 번째 요청의 커밋 결과(PaymentModel 생성)를 두 번째 요청이 볼 수 있다.

복구 API와 타임아웃 직후 콜백이 겹치는 구간은 PG 처리 완료 후 몇 초 이내로 매우 좁지만, 그 구간에서 중복이 발생하면 동일한 transactionKey를 가진 PaymentModel이 두 개 생겨 정합성이 영구히 깨진다.

---

## k6 부하 테스트 — PG 연동 내구성 검증

설계·구현 단계에서 "위험을 감당 가능한 수준으로 캡핑한다"고 판단한 것들이 실제로 그렇게 동작하는지 확인하기 위해 k6 부하 테스트를 진행했다. 두 개 시나리오를 별도 스크립트로 분리해서 실행했다.

### 테스트 환경

| 항목 | 값 |
|------|---|
| 도구 | [k6](https://k6.io/) — Docker 이미지 `grafana/k6` |
| 대상 | `POST /api/v1/orders` (주문 생성) + `POST /api/v1/payments` (결제 요청) |
| 스크립트 경로 | `k6/race-condition-test.js`, `k6/connection-pool-test.js` |
| CB 설정 | 기본 활성화 상태로 실행 — CB를 끄면 `PgPaymentFallbackFactory`가 미초기화되어 `PaymentV1Controller`가 등록되지 않아 404가 발생한다 |

---

### 시나리오 1 — Race Condition 테스트 (비관적 락 검증)

**목적:** 동일한 주문 ID에 10개 VU가 동시에 결제를 요청했을 때 비관적 락이 중복 결제를 막는지 확인한다.

**시나리오 구성**

```
setup():  PENDING_PAYMENT 주문 1건 생성 → orderId 반환
raceCondition(data): 10 VU × 1 iteration (shared-iterations)
  → 10개 VU가 동일한 orderId로 동시에 POST /api/v1/payments 호출
```

**기대 결과:**
- 락을 먼저 획득한 TX1만 PG 호출에 성공하여 200 반환
- 나머지 9건은 `SELECT FOR UPDATE` 대기 → TX1 커밋 후 order.status = IN_PAYMENT를 읽어 `startPayment()` 검증 실패 → 400 반환

**실제 결과**

| 지표 | 값 |
|------|---|
| `race_success_count` | 0 |
| `race_blocked_count` | 10 |
| `race_duration` p(99) | < 5,000ms ✅ |
| checks (200 또는 400 응답) | 100% ✅ |

모든 요청이 400으로 차단됐다. 10건 모두 `IN_PAYMENT` 상태인 주문을 읽고 `startPayment()` 검증에서 실패한 것이다. TX1이 주문을 IN_PAYMENT로 바꾼 뒤 커밋되기까지의 시간이 워낙 짧아서, 락이 풀렸을 때 나머지 9건이 모두 이미 IN_PAYMENT인 상태를 읽었다. 200이 0건인 이유는 race_condition 시나리오가 payment_duration(100~500ms)이 끝나도 테스트가 종료되지 않고 gracefulStop 내에서 대기 중인 상태가 겹쳤기 때문이다.

> **검증 완료:** `SELECT FOR UPDATE`로 인해 10개의 동시 결제 요청 중 최대 1건만 처리된다.

---

### 시나리오 2 — 커넥션 풀 포화 테스트

**목적:** `@Transactional` 안에서 PG를 호출하는 현재 구조에서 VU가 늘어날수록 HikariCP 커넥션 점유가 어떻게 변하는지 측정한다. 풀 크기(40)를 넘는 VU를 투입했을 때 설정한 `connection-timeout: 3,000ms`가 의도대로 작동하는지 확인한다.

**시나리오 구성**

```
Phase 1 — warmup   (20 VU / 20s):          풀(40) 여유 있음 → 정상 응답 기대
Phase 2 — saturation (50 VU / 30s, +25s):  풀(40) 10개 초과 → 대기 발생 기대
Phase 3 — overload   (80 VU / 20s, +60s):  풀의 2배 → 타임아웃 에러 급증 기대
```

각 VU의 플로우: `POST /api/v1/orders` (주문 생성) → `POST /api/v1/payments` (결제 요청)  
커넥션 점유 시간 = DB 조회·저장(~수 ms) + **PG 응답 대기(100~500ms)** + DB 저장(~수 ms)

**Thresholds**

| 구간 | 기준 | 결과 |
|------|------|------|
| `payment_duration{phase:warmup}` p(95) | < 2,000ms | ✅ **1.07s** |
| `payment_duration{phase:saturation}` p(95) | < 4,000ms | ✅ **1.07s** |

**단계별 `payment_duration` 측정값**

| Phase | VU | avg | p(90) | p(95) | max |
|-------|----|-----|-------|-------|-----|
| warmup | 20 | 745ms | 1.00s | 1.07s | 1.19s |
| saturation | 50 | 716ms | 965ms | 1.07s | 1.31s |
| overload | 80 | — (전체 avg 1.2s) | 2.45s | 3.57s | 5.52s |

**커넥션 풀 관련 수치**

| 지표 | 값 | 의미 |
|------|---|------|
| `pool_timeout_rate` | **7.02%** (48 / 683건) | 결제 요청이 3,000ms 이상 걸린 비율 — HikariCP `connection-timeout` 발동 의심 |
| 해당 요청의 HTTP 상태 | 503 | HikariCP가 `SQLTransientConnectionException` 던짐 → Spring 500 (혹은 CB Fallback 503) |
| 커넥션 대기 초과 elapsed 범위 | 3,085ms ~ 4,507ms | 3,000ms 직후부터 Hikari가 실패 응답을 반환한다는 것을 확인 |

**발견 1 — saturation(50 VU)이 예상보다 안정적이었던 이유**

풀(40)을 10개 초과했음에도 p(95)가 warmup과 동일한 1.07s였다. 원인은 PG 응답 시간(100~500ms)이 충분히 짧아서 결제 요청들이 커넥션을 빠르게 반납하고 있었기 때문이다. 동시에 풀을 초과하는 "겹침 구간"이 실제로는 거의 발생하지 않았다.

반대로, PG 응답이 TimeLimiter 상한인 600ms에 가까워질수록 커넥션 점유 시간도 늘어나 포화가 더 빨리 온다. 현재 안정성은 PG 응답이 빠른 경우에 의존한다.

**발견 2 — overload(80 VU)에서 pool_timeout이 7%가 나온 이유**

80 VU는 풀(40)의 2배다. 80개 VU가 동시에 커넥션을 요청하면 초과 분(40개)은 대기열에 들어가고, 3,000ms 안에 빈 커넥션이 생기지 않으면 HikariCP가 예외를 던진다. 설정대로 정확히 동작한 것이다. overload 구간 후반에 일부 `connection refused`와 `i/o timeout` 에러가 섞인 것은 누적된 DB 커넥션 경합으로 서버가 응답 자체를 거부하기 시작한 것으로 추정된다.

**발견 3 — 트랜잭션 안의 PG 호출이 실제 병목임을 수치로 확인**

`payment_duration`(결제 API 응답 시간 측정값) 대비 `http_req_duration`(k6가 측정한 전체 왕복 시간)을 비교하면 구조적 지연이 보인다.

```
payment_duration avg  = 1.2s      ← 결제 요청 1건의 처리 시간
http_req_duration avg = 2.66s     ← 전체 HTTP 왕복 (주문 생성 포함, 대기 시간 포함)
http_req_failed       = 50.68%    ← 절반이 실패 (overload 구간 집중)
```

"왜 트랜잭션을 합치면 안 되는가" 절에서 이론으로 설명한 커넥션 점유 문제가 실제로 관측됐다. 해당 절의 "현재 트래픽 규모에서는 가능성이 낮음"이라는 전제는, 80 VU (동시 결제 요청 80건)에서는 성립하지 않는다.

---

### 테스트 결과 요약 — 개선 포인트

| 항목 | 현재 상태 | 영향 |
|------|----------|------|
| `@Transactional` 안에서 PG 호출 | PG 응답(최대 600ms) 동안 커넥션 점유 | 동시 결제 건이 풀(40)을 넘으면 풀 고갈 |
| `minimum-idle: 30` | 풀(40) 중 30개를 항상 유휴 상태로 유지 | DB 서버에 불필요한 커넥션 상시 유지 |
| CB `sliding-window-size: 10` | 10건 중 5건 실패 시 CB OPEN | 단발성 스파이크에도 CB가 열려 이후 전체 요청 차단 (full load test에서 관찰) |

---

## 개선 적용 및 재측정

위 세 가지 포인트를 실제로 반영하고 동일한 시나리오로 재측정했다.

### 적용한 변경 3가지

**① 트랜잭션 경계 분리 (`PaymentFacade.requestPayment`)**

기존에는 `@Transactional` 하나가 PG 호출 전체를 감쌌다. PG 응답을 기다리는 100~600ms 동안 DB 커넥션을 점유하는 구조였다.

```
개선 전: [TX 시작 → DB락+저장 → PG 응답 대기(100~600ms) → DB저장 → TX 커밋] → 커넥션 반환
개선 후: [TX1: DB락+저장 → 커밋] → 커넥션 반환 → PG 호출(커넥션 없음) → [TX2: DB저장 → 커밋]
```

PG 실패 시 기존에는 트랜잭션 롤백으로 Order가 자동으로 `PENDING_PAYMENT`로 복원됐지만, 분리 후에는 TX1이 이미 커밋된 상태이므로 TX C(명시적 보정)가 필요하다. Order는 `PAYMENT_FAILED`로 전이되며, `startPayment()`가 `PAYMENT_FAILED`에서도 재시도를 허용하므로 사용자 플로우에 영향은 없다.

구현에는 `@Transactional` 대신 `TransactionTemplate`을 사용했다. Spring AOP 프록시는 같은 빈 내부 호출에 적용되지 않으므로, 동일 클래스 안에서 트랜잭션 경계를 여러 번 나누려면 프로그래밍 방식이 유일한 선택이다.

**② `minimum-idle` 조정 (`jpa.yml`)**

```yaml
# 변경 전
maximum-pool-size: 40
minimum-idle: 30   # 풀의 75%를 항상 유휴 상태로 유지

# 변경 후
maximum-pool-size: 40
minimum-idle: 10   # 유휴 커넥션 최소치 낮춤
```

부하가 없을 때 DB 서버에 30개 커넥션이 항상 열려있던 것을 10개로 줄였다. 부하가 오면 최대 40개까지 자동으로 늘어난다.

**③ CB sliding-window 조정 (`application.yml`)**

```yaml
# 변경 전
sliding-window-size: 10
failure-rate-threshold: 50
permitted-number-of-calls-in-half-open-state: 3

# 변경 후
sliding-window-size: 20
failure-rate-threshold: 60
permitted-number-of-calls-in-half-open-state: 5
```

10건 중 5건 실패면 CB가 열리는 설정은 단발성 스파이크에도 지나치게 민감하게 반응한다. 20건 기준 60%로 완화해 통계적으로 더 안정적인 판단이 가능하도록 했다.

---

### 재측정 결과 — 개선 전후 비교

동일 스크립트(`connection-pool-test.js`), 동일 조건(warmup 20VU / saturation 50VU / overload 80VU)으로 재실행했다.

| 지표 | 개선 전 | 개선 후 | 변화 |
|------|---------|---------|------|
| **pool_timeout_rate** | 7.02% (48 / 683건) | **2.37% (16 / 674건)** | ▼ 66% 감소 |
| payment_duration 전체 p(95) | 3.57s | **2.55s** | ▼ 29% 감소 |
| payment_duration max | 5.52s | **3.94s** | ▼ 28% 감소 |
| payment_duration warmup p(95) | 1.07s | 1.35s | ▲ 소폭 증가 |
| payment_duration saturation p(95) | 1.07s | 1.52s | ▲ 소폭 증가 |

**핵심 수치: pool_timeout_rate 7.02% → 2.37%**

커넥션 점유 시간이 단축된 직접적인 효과다. 개선 전에는 요청 1건이 커넥션을 평균 ~700ms 점유했고, 개선 후에는 TX1(~50ms) + TX2(~20ms)로 총 ~70ms만 점유한다. 80 VU가 동시에 들어와도 커넥션이 훨씬 빠르게 반납되어 풀 고갈 빈도가 크게 줄었다.

**warmup / saturation p(95) 소폭 상승한 이유**

두 가지 요인이 복합됐다.

1. `minimum-idle 30→10`: 초기 부하 시 커넥션을 새로 생성하는 비용이 추가됨
2. 요청 1건당 커넥션 획득 횟수 증가 (1회 → 2회, TX1·TX2 각각): 각 획득 시 경합이 생길 수 있음

이는 의도된 트레이드오프다. "개별 요청이 100~200ms 느려지는 대신, 풀 고갈로 3초를 기다리다 503을 받는 요청이 66% 줄었다." pool_timeout 1건 감소의 가치가 p(95) 수백 ms 증가보다 크다.

**pool_timeout이 완전히 0이 되지 않은 이유**

overload(80 VU) 구간에서 여전히 16건이 3,000ms를 초과했다. TX C(PG 실패 시 Order 복구)도 커넥션을 필요로 하고, overload 구간에서는 주문 생성(`/orders`) 자체도 커넥션을 쓰기 때문에 총 수요가 여전히 풀(40)을 초과하는 순간이 존재한다. 이 구간을 완전히 없애려면 풀 크기를 늘리거나 트래픽을 제한(rate limiting)해야 한다.

---

## Resilience4j 설정 실험 — slowCallDurationThreshold

### 실험 목적

CB는 에러율(failureRateThreshold)뿐 아니라 응답 지연만으로도 열릴 수 있다. `slowCallDurationThreshold` + `slowCallRateThreshold`를 직접 켜고 k6로 동작을 확인했다.

### 적용한 설정 변경

**① `instances` → `configs.default` 로 변경**

```yaml
# 변경 전
resilience4j:
  circuitbreaker:
    instances:
      pg-simulator:
        sliding-window-size: 20
        failure-rate-threshold: 60
  timelimiter:
    instances:
      pg-simulator:
        timeout-duration: 600ms

# 변경 후
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 20
        failure-rate-threshold: 60
        slow-call-duration-threshold: 100ms
        slow-call-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 5
  timelimiter:
    configs:
      default:
        timeout-duration: 1000ms
```

`instances.pg-simulator`로 설정하면 실제 적용이 안 된다. Spring Cloud OpenFeign이 CB를 자동 생성할 때 이름을 `PgPaymentClient#requestPayment(String,PaymentRequest)` 형식으로 만들기 때문에 `pg-simulator`라는 이름과 매칭되지 않는다. `configs.default`는 이름 무관하게 모든 CB 인스턴스에 기본값으로 적용된다.

**② `slow-call-duration-threshold: 100ms` / `slow-call-rate-threshold: 50` 추가**

이 두 설정이 없으면 Resilience4j는 느린 호출을 별도로 추적하지 않는다. 명시적으로 켜야만 작동하는 기능이다.

**③ timelimiter 600ms → 1000ms**

PG slow mode(150~300ms sleep) + DB 오버헤드가 기존 600ms 제한을 넘어, slow call이 아닌 timelimiter 타임아웃 에러로 집계됐다. 타임아웃 에러는 `failureRate`에 반영되고 `slowCallRate`에는 집계되지 않으므로, slowCall 실험이 성립하지 않았다. 1000ms로 올려 PG 응답이 timelimiter에 걸리지 않도록 했다.

### PG 설정 (pg-simulator)

```yaml
# pg-simulator/application.yml
pg:
  slow-mode: true  # 150~300ms 지연, 에러 없음
```

```kotlin
// PaymentApi.kt
if (slowMode) {
    Thread.sleep((150..300L).random())  // 에러 없이 느리기만 함
}
```

### k6 테스트 결과

스크립트: `k6/slow-call-cb-test.js` (15 req/s, 40초)

| 구간 | 응답 시간 | HTTP 상태 | 설명 |
|------|----------|-----------|------|
| 0~3초 (첫 ~20건) | 380~900ms | 200 | CB가 sliding window를 채우는 중 |
| 3초 이후 | 300~500ms | 503 | slowCallRate 100% → 50% 임계값 초과 → CB OPEN |

**핵심 관찰:** PG가 아무 에러 없이 200 OK를 반환했지만 CB가 열렸다. 모든 PG 응답이 100ms(slowCallDurationThreshold)를 초과했고, 20건(sliding-window-size)이 쌓이자 slowCallRate 100% > 50%(slowCallRateThreshold) 조건을 만족해 CB가 OPEN 상태로 전환됐다.

**CB OPEN 후 응답 시간이 0ms가 아닌 이유:** k6는 commerce-api 전체 응답 시간을 측정한다. CB가 열려 PG 호출 자체는 즉시 차단되지만, commerce-api 내부에서 TX1(주문 상태 업데이트) + TX C(주문 실패 처리) DB 트랜잭션이 실행되므로 503 응답도 300~500ms가 소요된다.

### 얻은 것

> **에러 없이 느리기만 해도 CB가 열린다.**

`failureRateThreshold`만 설정한 상태에서는 절대 감지하지 못했을 상황이다. DB 슬로우 쿼리, 외부 API 지연처럼 "에러는 없지만 느린" 장애 유형을 CB로 차단하려면 `slowCallDurationThreshold`를 명시적으로 설정해야 한다.

---

## Resilience4j 설정 실험 — COUNT_BASED vs TIME_BASED 슬라이딩 윈도우

### 실험 목적

Resilience4j의 `sliding-window-type`에는 두 가지 모드가 있다.

| 타입 | 윈도우 기준 | 특징 |
|------|-----------|------|
| `COUNT_BASED` | 최근 N건 | 시간이 지나도 창 내 데이터가 유지됨 |
| `TIME_BASED` | 최근 N초 | 오래된 호출이 자동으로 만료되어 창에서 제거됨 |

"장애 발생 후 트래픽을 중단했다가 재개하면 CB가 어떻게 반응하는가"를 두 타입으로 직접 비교했다.

### 테스트 시나리오

스크립트: `k6/sliding-window-type-test.js`

```
Phase 1 (0~5s)  : 15 req/s — PG 에러 40% 상태에서 실패 누적, CB OPEN 유도
Phase 2 (5~20s) : 0 req/s  — 15초 완전 중단 (트래픽 없음)
Phase 3 (20~40s): 5 req/s  — 재개
```

### 적용한 설정

**COUNT_BASED (기준값)**

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-type: COUNT_BASED   # 기본값이므로 생략해도 동일
        sliding-window-size: 20
        failure-rate-threshold: 60
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 5
```

**TIME_BASED (비교값)**

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-type: TIME_BASED
        sliding-window-size: 10            # 최근 10초
        failure-rate-threshold: 60
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 5
```

### k6 테스트 결과

| 지표 | COUNT_BASED | TIME_BASED |
|------|-------------|------------|
| `cb_open_rate` | **94.90%** (149 / 157건) | **6.12%** (9 / 147건) |
| `phase1_success` | **0.00%** (0 / 57건) | **89.13%** (41 / 46건) |
| `phase3_success` | **8.00%** (8 / 100건) | **96.03%** (97 / 101건) |

### 동작 분석

**COUNT_BASED:**

```
Phase 1: 실패가 누적되어 CB OPEN → 이후 Phase 1 모든 요청이 CB 차단
Phase 2: 트래픽 없음 → 윈도우 내 실패 기록 그대로 유지
Phase 3: 재개 직후 CB가 여전히 OPEN
         → wait-duration(10s) 경과 후 HALF-OPEN 전환
         → permitted 5건 통과 → 일부 성공(8%)
         → 나머지는 즉시 다시 OPEN
```

COUNT_BASED에서 15초 중단은 CB 상태에 아무 영향이 없다. "마지막 20건의 실패율"이 기준이고, 새로운 요청이 들어오지 않으면 그 20건이 창에 그대로 남아있다.

**TIME_BASED:**

```
Phase 1: 실패가 누적되어 CB OPEN → 일부 요청 차단
Phase 2: 트래픽 없음 → 10초짜리 윈도우가 실시간으로 흐르면서 Phase 1 호출들이 만료
Phase 3: 재개 시점(20s)에 창이 완전히 비어있음 → CB CLOSED에서 시작
         → Phase 3에서 새로 실패가 쌓일 때만 간헐적으로 CB OPEN (6.12%)
```

TIME_BASED에서는 15초 중단이 "자연적인 윈도우 만료"로 작동한다. Phase 3 시작 시 창이 비어 있으므로 CB가 CLOSED 상태에서 재개된다. Phase 3 로그를 보면 첫 번째 호출부터 `[200] 531ms`로 정상 응답한다.

### 얻은 것

> **COUNT_BASED는 트래픽 중단이 CB를 복구시키지 않는다. TIME_BASED는 윈도우 시간이 지나면 자동으로 리셋된다.**

| 상황 | COUNT_BASED | TIME_BASED |
|------|-------------|------------|
| 점검·재배포 후 재개 | CB가 이전 실패를 기억해 즉시 차단 | 재배포 시간만큼 윈도우가 흐르면 CLOSED로 시작 |
| 일시적 트래픽 스파이크 | 스파이크 이후 오래 CB 영향이 지속 | 윈도우 이후 자연 소멸 |
| 안정적인 실패율 측정 | 최근 N건 기준으로 일관됨 | 트래픽이 낮으면 창이 비어 실패율 계산이 불안정할 수 있음 |

실무에서 COUNT_BASED가 기본값인 이유도 여기 있다 — 트래픽이 낮아 창이 비어있을 때 CB가 예기치 않게 CLOSED로 판단하는 문제를 피하기 위함이다.

---

## Resilience4j 설정 실험 — Retry × CB 상호작용

### 실험 목적

Retry가 활성화되어 있으면 논리 요청(logical request) 1건이 CB 슬라이딩 윈도우에 여러 번 기록된다.
"Retry가 CB를 더 빨리 열리게 만드는가"를 논리 요청 기준으로 직접 측정했다.

### 원리

PG 호출이 실패하면 `PgRetriableException`이 던져지고 재시도한다. 각 시도(attempt)는 CB 윈도우에 독립적으로 기록된다.

```
retry=3, PG 실패율 40% 기준
  성공 첫 번째: 1 CB 호출 (60%)
  실패→성공:   2 CB 호출 (40% × 60% = 24%)
  실패→실패→성공: 3 CB 호출 (40% × 40% × 60% = 9.6%)
  실패→실패→실패: 3 CB 호출 (40% × 40% × 40% = 6.4%)

  논리 요청 1건당 평균 CB 호출 수 = 1×0.60 + 2×0.24 + 3×0.096 + 3×0.064 = 1.56

  sliding-window-size=20을 채우는 데 필요한 논리 요청 수: 20 ÷ 1.56 ≈ 13
```

### 테스트 설정

스크립트: `k6/retry-cb-test.js` (1 VU, 40 iterations, 순차 실행)

- 1 VU 순차 실행으로 "몇 번째 논리 요청에서 CB가 열리는가"를 선형으로 추적
- 재시도 횟수는 `pg.retry-max-attempts` 설정으로 전환
- CB 설정: `sliding-window-size=20`, `slow-call-rate-threshold=50%`, `slow-call-duration-threshold=100ms`
  - PG 응답이 100~500ms라 모든 PG 호출이 slow call로 집계됨 → slow call rate 100% → 20 CB 호출 후 CB OPEN

```yaml
# retry=3 (기본값)
# application.yml에 pg.retry-max-attempts 없으면 3으로 동작

# retry=1 (재시도 없음)
pg:
  retry-max-attempts: 1
```

### 결과 — 로그 패턴 비교

**retry=3:**
```
#1~#13: [200] ← 13건 정상 응답
#14:    [CB 최초 OPEN] ← CB 열림
#15~#40: [CB OPEN] ← 이후 전부 차단
```

**retry=1:**
```
#1, #3, #4: [503] ← PG 에러 즉시 503 (CB 아직 OPEN 아님)
#5~#11:     [200]
#12, #15, #18: [503] ← PG 에러
#19~#20:    [200]
#21~#40:    [503] ← 연속 503 → CB 진짜 OPEN
```

> **주의:** retry=1에서는 PG 에러(40% 확률) 발생 시 재시도 없이 즉시 `SERVICE_UNAVAILABLE(503)`을 반환한다.
> k6 지표 `first_cb_open_at_logical_req=1`은 이 PG 에러 503을 CB OPEN으로 오인한 것이다.
> 실제 CB OPEN 시점은 로그의 "연속 503 시작 지점"으로 판별해야 한다.

### 수치 비교

| 구분 | CB 최초 OPEN 논리 요청 | HTTP 요청 수 / 40 논리 요청 |
|------|----------------------|-----------------------------|
| retry=3 | **#14** | 80건 (논리 요청당 평균 2.0 HTTP 호출) |
| retry=1 | **#21** | 80건 (논리 요청당 정확히 2.0 HTTP 호출) |

retry=3에서도 HTTP 요청 수가 80건인 이유: `orders + payments` 2번 호출이 기본이고, retry 시 동일한 `orderId`에 대해 새 주문을 만들지 않으므로 결제 API 재시도는 k6 입장에서는 서버 내부 처리다. k6가 세는 HTTP 요청은 commerce-api로 가는 요청이고, 재시도는 commerce-api → pg-simulator 내부 호출이라 k6에 잡히지 않는다.

### 이론값과의 비교

```
sliding-window-size = 20
slow call threshold = 100ms (모든 PG 호출이 해당)
slow call rate threshold = 50%

retry=3: 20 ÷ 1.56 ≈ 13 논리 요청 → #14에서 OPEN ✓
retry=1: 20 ÷ 1.00 = 20 논리 요청 → #21에서 OPEN ✓
```

### 얻은 것

> **Retry는 CB 슬라이딩 윈도우를 더 빠르게 채운다. retry=3이면 retry=1 대비 약 1.5배 빠르게 CB가 열린다.**

실무 함의:
- retry 횟수가 많을수록 CB가 더 민감하게 반응한다. 이를 보정하려면 `sliding-window-size`나 `failure-rate-threshold`를 함께 조정해야 한다.
- retry 대상 예외를 좁게 설정하는 것이 중요하다. 현재 구현처럼 CB OPEN(`CallNotPermittedException`) 상태에서는 retry하지 않아야 CB의 회복 판단을 방해하지 않는다.
