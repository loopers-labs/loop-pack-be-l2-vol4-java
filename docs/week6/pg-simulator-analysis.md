# pg-simulator 모듈 분석

## 이 모듈은 무엇인가

실제 카드사·PG사(결제대행사)의 동작 방식을 흉내 내는 스텁 서버다. 커머스 서비스(8080)와 나란히 띄워두고 결제 연동을 실습하는 용도로 만들어졌다.

핵심 특징은 **동기 응답이 아니라는 것**이다. 결제를 요청하면 즉시 `PENDING`만 돌려주고, 1\~5초 뒤 비동기로 결과를 결정한 다음 미리 등록해둔 `callbackUrl`로 통보한다. 실제 PG사가 카드사 망을 거쳐 처리하는 방식과 같다.

---

## 모듈 기본 정보

| 항목 | 값 |
| --- | --- |
| 포트 | API 8082 / Actuator 8083 |
| 언어 | Kotlin 2.0.20 |
| DB 스키마 | `paymentgateway` (MySQL) |
| 의존 모듈 | `modules/jpa`, `modules/redis` |
| 실행 명령 | `./gradlew :apps:pg-simulator:bootRun` |

---

## 패키지 구조

```
com.loopers
├── interfaces
│   ├── api
│   │   ├── payment
│   │   │   ├── PaymentApi.kt            — REST 컨트롤러
│   │   │   └── PaymentDto.kt            — 요청/응답 DTO
│   │   └── argumentresolver
│   │       └── UserInfoArgumentResolver.kt  — X-USER-ID 헤더 파싱
│   └── event
│       └── payment
│           └── PaymentEventListener.kt  — 비동기 이벤트 리스너
├── application
│   └── payment
│       ├── PaymentApplicationService.kt — 유스케이스 조합
│       ├── PaymentCommand.kt            — 입력 커맨드
│       ├── TransactionInfo.kt           — 출력 DTO
│       └── OrderInfo.kt                 — 주문 단위 출력 DTO
├── domain
│   ├── payment
│   │   ├── Payment.kt                   — 결제 엔티티
│   │   ├── PaymentEvent.kt              — 도메인 이벤트 정의
│   │   ├── PaymentEventPublisher.kt     — 이벤트 발행 인터페이스
│   │   ├── PaymentRelay.kt              — 콜백 알림 인터페이스
│   │   ├── PaymentRepository.kt         — 저장소 인터페이스
│   │   ├── TransactionKeyGenerator.kt   — transactionKey 생성기
│   │   ├── CardType.kt                  — 카드사 열거형
│   │   └── TransactionStatus.kt         — 결제 상태 열거형
│   └── user
│       └── UserInfo.kt                  — 유저 식별자 래퍼
└── infrastructure
    └── payment
        ├── PaymentCoreRepository.kt     — PaymentRepository 구현체
        ├── PaymentCoreEventPublisher.kt — Spring ApplicationEventPublisher 위임
        ├── PaymentCoreRelay.kt          — RestTemplate 콜백 전송
        └── PaymentJpaRepository.kt      — JpaRepository 확장
```

---

## 결제 전체 흐름

```plaintext
커머스 서비스                          pg-simulator
     │                                      │
     │  POST /api/v1/payments               │
     │─────────────────────────────────────>│
     │                                      │  100~500ms 지연
     │                                      │  40% 확률로 즉시 500 반환
     │                                      │
     │                                      │  Payment(PENDING) 저장
     │                                      │  PaymentCreated 이벤트 발행
     │                                      │
     │  { transactionKey, status: PENDING } │
     │<─────────────────────────────────────│
     │                                      │
     │                                      │  [커밋 이후, 별도 스레드]
     │                                      │  1~5초 대기
     │                                      │  결과 결정 (70% 승인 / 30% 실패)
     │                                      │  Payment(SUCCESS or FAILED) 저장
     │                                      │  PaymentHandled 이벤트 발행
     │                                      │
     │                                      │  [커밋 이후, 별도 스레드]
     │  POST {callbackUrl}                  │
     │<─────────────────────────────────────│
     │  { transactionKey, status, reason }  │
```

결제 흐름에서 주목할 점 두 가지:

1. **커머스 서비스는 PENDING을 먼저 받는다.** 최종 결과는 나중에 콜백으로 온다. 커머스 서비스가 PENDING 상태의 주문을 어떻게 다룰지, 콜백을 어떻게 처리할지를 구현해야 한다는 의미다.

2. **결제 API 자체도 40%는 실패한다.** 네트워크 불안정이나 PG사 서버 장애를 흉내 낸 것이다. 커머스 서비스가 결제 요청 실패를 어떻게 처리하는지도 학습 대상이다.

---

## API 명세

### 결제 요청

```
POST /api/v1/payments
X-USER-ID: {userId}
Content-Type: application/json

{
  "orderId": "1351039135",       // 6자 이상 필수
  "cardType": "SAMSUNG",         // SAMSUNG | KB | HYUNDAI
  "cardNo": "1234-5678-9814-1451", // xxxx-xxxx-xxxx-xxxx 형식
  "amount": 5000,                // 양의 정수
  "callbackUrl": "http://localhost:8080/api/v1/.../callback"
}
```

응답:

```json
{
  "transactionKey": "20250816:TR:9577c5",
  "status": "PENDING",
  "reason": null
}
```

40% 확률로 500 응답:

```json
{ "message": "현재 서버가 불안정합니다. 잠시 후 다시 시도해주세요." }
```

### 트랜잭션 단건 조회

```
GET /api/v1/payments/{transactionKey}
X-USER-ID: {userId}
```

### 주문에 연결된 결제 목록 조회

```
GET /api/v1/payments?orderId={orderId}
X-USER-ID: {userId}
```

결제는 하나의 주문에 여러 건이 붙을 수 있다 (재시도 포함). 목록은 `updatedAt` 내림차순으로 반환된다.

---

## 핵심 컴포넌트 상세

### Payment 엔티티

일반적인 엔티티와 다른 점이 두 가지 있다.

**PK가 문자열이고 직접 생성한다.** `@Id` 컬럼이 `transactionKey`(String)이며 `@GeneratedValue`가 없다. `TransactionKeyGenerator`가 `yyyyMMdd:TR:uuid6` 형식으로 생성한다. DB에 저장하기 전에 식별자가 이미 확정되어 있으므로, 비동기 이벤트에 transactionKey만 담아도 해당 레코드를 찾을 수 있다.

```kotlin
// TransactionKeyGenerator.kt
fun generate(): String {
    val now = LocalDateTime.now()
    val uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 6)
    return "${DATETIME_FORMATTER.format(now)}:$KEY_TRANSACTION:$uuid"
    // 예) 20250816:TR:9577c5
}
```

`BaseEntity`**를 상속하지 않는다.** `createdAt`/`updatedAt`을 직접 필드로 선언하고 관리한다. 프로젝트 공통 `BaseEntity`와 무관하게 독립적으로 동작하는 모듈이기 때문이다.

**상태 전이는 엔티티 메서드가 책임진다.** `approve()`, `limitExceeded()`, `invalidCard()` 모두 PENDING 상태에서만 호출 가능하고, 아닐 경우 `CoreException`을 던진다.

```
PENDING ──→ SUCCESS  (approve, 70%)
        ──→ FAILED   (limitExceeded, 20%)
        ──→ FAILED   (invalidCard, 10%)
```

### PaymentApplicationService

유스케이스 세 개를 담당한다.

| 메서드 | 트랜잭션 | 역할 |
| --- | --- | --- |
| `createTransaction` | `@Transactional` | Payment 저장 + PaymentCreated 발행 |
| `handle` | `@Transactional` | 결과 결정 + PaymentHandled 발행 |
| `notifyTransactionResult` | 없음 | callbackUrl로 POST |

`handle`은 HTTP 요청이 아닌 이벤트 리스너에서 호출된다. API 컨트롤러에서는 `createTransaction`만 호출한다.

### PaymentEventListener

이벤트 두 개를 처리한다.

```kotlin
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun handle(event: PaymentEvent.PaymentCreated) {
    Thread.sleep((1000L..5000L).random())   // 카드사 처리 지연
    paymentApplicationService.handle(event.transactionKey)
}

@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun handle(event: PaymentEvent.PaymentHandled) {
    paymentApplicationService.notifyTransactionResult(event.transactionKey)
}
```

`AFTER_COMMIT`이 핵심이다. Payment가 DB에 실제로 저장된 이후에만 리스너가 실행된다. 트랜잭션이 롤백되면 이벤트 처리도 일어나지 않는다.

`@Async`로 별도 스레드에서 실행된다. 결과 결정과 콜백 전송이 원래 HTTP 요청 스레드를 블로킹하지 않는다.

### PaymentCoreRelay

콜백 전송을 담당한다. `RestTemplate`으로 `callbackUrl`에 POST를 보내고, 실패해도 로그만 남기고 넘어간다.

```kotlin
override fun notify(callbackUrl: String, transactionInfo: TransactionInfo) {
    runCatching {
        restTemplate.postForEntity(callbackUrl, transactionInfo, Any::class.java)
    }.onFailure { e -> logger.error("콜백 호출을 실패했습니다. {}", e.message, e) }
}
```

커머스 서비스가 꺼져 있거나 콜백 엔드포인트가 없어도 pg-simulator는 계속 동작한다. 콜백 수신 실패가 결제 상태를 바꾸지 않는다.

---

## 시뮬레이션 파라미터 요약

| 지점 | 동작 | 위치 |
| --- | --- | --- |
| 결제 요청 수신 | 100\~500ms 지연 | `PaymentApi.request()` |
| 결제 요청 수신 | 40% 확률 500 에러 반환 | `PaymentApi.request()` |
| 카드사 처리 | 1\~5초 지연 | `PaymentEventListener` |
| 결과 결정 | 70% 승인 / 20% 한도초과 / 10% 잘못된 카드 | `PaymentApplicationService.handle()` |
| 콜백 전송 | 실패 시 로그만 기록, 재시도 없음 | `PaymentCoreRelay` |

---

## 콜백 수신 페이로드

pg-simulator가 커머스 서비스의 `callbackUrl`로 보내는 POST 바디는 `TransactionInfo` 구조다.

```json
{
  "transactionKey": "20250816:TR:9577c5",
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451",
  "amount": 5000,
  "status": "SUCCESS",
  "reason": "정상 승인되었습니다."
}
```

`status`가 `FAILED`일 때 `reason` 값:

- 한도초과: `"한도초과입니다. 다른 카드를 선택해주세요."`
- 잘못된 카드: `"잘못된 카드입니다. 다른 카드를 선택해주세요."`

---

## 커머스 서비스가 구현해야 할 것

pg-simulator를 상대로 결제를 연동하려면 커머스 서비스 쪽에 세 가지가 필요하다.

1. **콜백 엔드포인트**: `callbackUrl`로 지정한 경로에서 POST를 받아야 한다. 기본 설정은 `http://localhost:8080`으로 시작해야 한다.

2. **PENDING 상태 관리**: 결제 요청 직후 주문은 PENDING 상태다. 콜백이 오기 전까지 이 주문을 어떻게 처리할지 정책이 있어야 한다.

3. **콜백 처리 로직**: `transactionKey`로 자체 주문을 찾아 상태를 갱신하는 로직이 필요하다. 콜백이 늦게 오거나 중복으로 올 가능성도 고려해야 한다.