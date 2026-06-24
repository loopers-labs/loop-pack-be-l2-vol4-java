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
| Retryer | PG 요청 실패 시 재시도 (멱등성 전제 필요) |
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

구현 전에는 "PG 호출이 실패하면 Order를 어떤 상태로 보낼지"가 정해지지 않았었다. 구현해보니 답은 단순했다 — **PG 호출이 실패하면 트랜잭션 전체가 롤백되면서 Order가 자동으로 PENDING_PAYMENT로 돌아간다.** 별도의 "실패 처리 로직"을 만들 필요가 없었다. 아래 케이스들이 이 동작을 보여준다.

#### 케이스 A — PG 즉시 에러 (40% 확률)

```
@Transactional {
    order.startPayment()         → IN_PAYMENT (커밋 예정)
    pgPaymentClient.request()    → 100~500ms 후 INTERNAL_ERROR 응답
}
→ 예외 발생 → 트랜잭션 롤백 → Order: PENDING_PAYMENT 복원
```

PG에 결제 건이 생성되지 않았으므로 추가 처리 없이 사용자가 재시도 가능. **별도 대응 불필요.**

#### 케이스 B — PG 응답이 600ms를 넘게 걸림 (TimeLimiter 타임아웃)

1. commerce-api가 PG에 결제를 요청한다.
2. 600ms 안에 응답이 안 온다 → TimeLimiter가 타임아웃으로 끊는다.
3. 트랜잭션이 롤백되어 Order는 PENDING_PAYMENT로 돌아간다.
4. **그런데 PG는 그 요청을 이미 받아서 처리하고 있을 수 있다.** commerce-api가 기다리길 포기한 것뿐이지, PG 입장에서는 정상 진행 중인 결제다.
5. PG가 처리를 끝내고 콜백을 보내지만, commerce-api에는 이 거래의 `PaymentModel`이 없어서(저장되기 전에 롤백됨) 콜백이 "결제 정보 없음"으로 무시된다.

Order가 PENDING_PAYMENT이므로 사용자가 재결제할 수 있다. 다만 재결제하면 PG에는 같은 주문에 대한 거래가 2개 생기게 된다 — 실제 서비스라면 첫 번째 건에 환불 처리가 필요하지만 이 과제 범위 밖이다.

→ 처음엔 "PaymentModel 자체가 없어 복구 API로 손댈 수 없다"고 범위 밖으로 미뤘다가, **결정 6에서 orderId 기반 조회로 실제로 해결했다.**

#### 케이스 C — CircuitBreaker OPEN

```
PG 실패율 50% 초과 → CircuitBreaker OPEN
→ 이후 요청은 Fallback 즉시 발동, pgPaymentClient.request() 호출 자체가 일어나지 않음
→ Fallback이 예외 던짐 → 롤백 → Order: PENDING_PAYMENT
```

PG 호출이 아예 일어나지 않아 내부/외부 상태 불일치가 없는 가장 깔끔한 실패 케이스.

**설정 버그로 한동안 동작 안 함 → 수정.** `spring.cloud.openfeign.circuitbreaker.enabled: true`가 local/test 프로파일에만 선언되어 있어 dev/qa/prd에서는 CB와 Fallback이 동작하지 않았다. 전역 `spring:` 블록으로 옮겨 모든 프로파일에 적용했다.

#### 케이스 D — PG 요청 성공, 내부 저장 실패

```
@Transactional {
    order.startPayment()         → IN_PAYMENT
    pgPaymentClient.request()    → 성공, transactionKey 반환
    paymentRepository.save()     → DB 일시 장애 등으로 실패
}
→ 예외 발생 → 트랜잭션 롤백 → Order: PENDING_PAYMENT, PaymentModel 없음
```

발생 확률은 매우 낮지만(DB 일시 장애), 케이스 B와 결과가 동일하다 — **같은 방법(결정 6)으로 해결.**

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

### 최종 결정 — 선택지 1 (현재 구조 유지)

선택지 2는 케이스 E 하나를 구조적으로 해결하기 위해 트랜잭션 경계를 전부 재설계하지만, B/D는 어느 선택지에서도 복구 API 없이는 해결되지 않는다. 추가 복잡도 대비 실질적 이득이 없다고 판단했다.

| 케이스 | 해결 방법 |
|--------|-----------|
| A. PG 즉시 에러 | 기존 롤백 구조로 자동 복원 |
| B. TimeLimiter 초과 | 결정 6 (orderId 기반 PG 재조회) |
| C. CircuitBreaker OPEN | application.yml 프로파일 버그 수정 |
| D. 내부 저장 실패 | 결정 6 (B와 동일) |
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

**결론.** 600ms 캡이 모든 프로파일에 실제로 적용되고, 동시 결제량이 풀 크기(40) 이하인 한 선택지 1의 위험은 감당 가능한 수준이다. 선택지 2는 점유 시간을 0으로 줄이지만 B/D의 복구 필요성은 그대로 남고 새로운 실패 케이스(TX C 실패 시 영구 고착)가 추가되므로, 추가 복잡도를 들일 이유가 없다고 보고 **선택지 1을 유지**했다.

---

## 구현 후 결정 사항

### 결정 4. 비관적 락 적용 범위
- 결제 요청(`requestPayment`)에서만 `SELECT FOR UPDATE`를 사용한다.
- 콜백 처리(`handleCallback`), 복구(`recoverPayment`) 등 다른 조회에는 일반 `find()`를 유지한다.
- 이유: 결제 요청만이 동시 접근으로 인한 중복 PG 호출을 유발할 수 있는 경로이기 때문이다.

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
