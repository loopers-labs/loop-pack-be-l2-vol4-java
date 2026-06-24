# Requirements — Payment (외부 PG 연동)

이 문서는 카드 결제(외부 PG 연동) 기능의 요구사항과 정책 결정을 기록한다. 공통 정책은 `docs/week2/01-requirements.md`를 따른다 — soft delete, `BaseEntity`, 금액은 `BIGINT` KRW, 헤더 인증, `ApiResponse`. 주문·쿠폰·보상 트랜잭션은 `docs/week4`를 그대로 잇는다.

이번 작업으로 결제는 **별도 결제 API**로 분리된다. 기존에 주문 흐름 안에서 결제를 호출하던 `StubPaymentGateway`와 `PlaceOrderFacade`의 결제 호출은 제거된다. 분리에 따른 주문 흐름 변화는 아래 [결제 분리가 주문 흐름에 미치는 영향](#결제-분리가-주문-흐름에-미치는-영향)에서 정리한다.

## 문제 정의 — 기능이 아니라 불확실성

외부 PG는 믿을 수 없는 시스템이라고 전제한다. 요청이 지연되거나 실패할 수 있고, 같은 요청이 중복으로 실행될 수도 있다. 승인은 됐는데 그 결과 응답만 유실되는 경우도 있다. 그래서 결제에서 진짜 어려운 건 카드 승인 자체가 아니다. 내부 주문 상태와 외부 PG 거래 상태가 서로 어긋나지 않게 맞추는 일 — 그 정합성이 핵심이다.

세 관점으로 다시 풀면 이렇다.

| 관점 | 재해석된 문제 |
| --- | --- |
| 사용자 | 결제를 요청하면 그 결과(성공/실패)가 주문에 정확히 반영되어야 한다. 응답이 늦거나 끊겨도 이중 청구되지 않아야 한다. |
| 비즈니스 | 한 주문은 정확히 1회만 결제되어야 한다(이중 결제 = 손실). 결제 실패 시 선점한 재고·쿠폰은 복원되어야 한다. PG 장애가 주문 전체를 마비시키지 않아야 한다. |
| 시스템 | PG는 비동기다 — 요청 즉시 `PENDING` transactionKey만 주고, 최종 결과는 1~5초 뒤 콜백으로 온다. **콜백은 무보증(fire-and-forget, 재전송 없음)**이므로 유실을 전제로 정합성을 보정해야 한다. |

## PG 시스템의 실제 동작 (전제)

`pg-simulator`(별도 Spring Boot App)를 분석해 보니, 설계가 반드시 받아들여야 할 동작이 다섯 가지였다.

- **비동기다.** `POST /api/v1/payments`는 `transactionKey`와 `PENDING`을 즉시 돌려주고, 실제 승인이나 실패는 1~5초 뒤에 결정된다.
- **요청 자체가 불안정하다.** 요청의 일정 비율이 곧바로 `INTERNAL_ERROR`(5xx)로 실패한다. 그래서 제한적인 재시도가 필요하다.
- **콜백을 보장하지 않는다.** 결과를 `callbackUrl`로 한 번 POST하고, 실패하면 로그만 남긴다. 재전송도 ACK도 없다.
- **orderId로 멱등하지 않다.** PG는 요청할 때마다 새 `transactionKey`를 발급한다. 같은 orderId로 N번 요청하면 거래가 N개 생긴다. 그래서 멱등을 보장할 책임은 호출자인 commerce-api에 있다.
- **조회 API를 제공한다.** 단건은 `GET /payments/{transactionKey}`, 주문 단위는 `GET /payments?orderId=`로 조회한다. 정합성 보정은 이 조회에 기댄다.

## 핵심 개념 — 두 개의 분리된 상태 기계

결제에는 서로 다른 두 개의 상태 기계가 있다. 하나는 우리가 가진 내부 기록이고, 다른 하나는 PG가 가진 외부 거래다.

- **내부 `Payment`** — commerce-api가 소유하는 결제 시도 레코드다. 자체 식별자(surrogate id)를 두고, 여기에 `orderId`, `transactionKey`(nullable), `pgProvider`(nullable), 상태(`PENDING`/`SUCCESS`/`FAILED`)를 담는다.
- **외부 PG 거래** — PG가 소유한다. `transactionKey`로 식별하고, 상태는 마찬가지로 `PENDING`/`SUCCESS`/`FAILED`다.

두 상태를 잇는 연결고리가 `Payment.transactionKey`다. 이 키가 없으면 내부와 외부를 맞대어 볼 방법이 없다.

## 제공 기능

| 기능 | 인증 | 엔드포인트 | 설명 |
| --- | --- | --- | --- |
| 결제 요청 | USER 필요 | `POST /api/v1/payments` | `{orderId, cardType, cardNo}`. 주문을 검증하고 PG에 결제를 요청한 뒤 **접수 결과**(PENDING)를 응답한다. |
| 결제 결과 콜백 수신 | PG 호출(서버 간) | `POST /api/v1/payments/callback/{provider}` | PG가 최종 결과를 통보한다. Payment/Order 상태를 확정한다. |

> `amount`는 요청 바디로 받지 않는다. 서버가 주문의 `finalAmount`에서 직접 계산해 PG로 전달한다. 클라이언트가 보낸 금액을 믿으면 위변조 위험이 있어서다.
> 콜백 URL에는 `{provider}`를 박는다. 어느 PG가 보낸 콜백인지 경로로 구분하려는 것으로, 멀티 PG를 대비한 설계다.

## 도메인 규칙

### Payment 상태

- 저장되는 상태는 `PENDING`, `SUCCESS`, `FAILED` 셋이다.
- 전이는 한 방향으로 한 번뿐이다. `PENDING`에서 `SUCCESS`로 가거나 `PENDING`에서 `FAILED`로 간다. 이미 terminal 상태(`SUCCESS`나 `FAILED`)면 어떤 통보가 와도 무시한다 — 이게 멱등 가드다.
- `transactionKey`와 `pgProvider`는 생성 시점엔 `null`이다. PG 호출이 성공한 뒤에 채워진다.

### 주문당 결제 1건 (멱등)

- 한 주문(`orderId`)에는 활성 상태(`PENDING` 또는 `SUCCESS`)의 Payment가 최대 1건만 존재한다. 별도 멱등키를 두지 않고 orderId 자체를 멱등 단위로 삼는다.
- 이 규칙은 DB로 강제한다. `payments(order_id)`에 활성 상태로 한정한 유니크를 건다(또는 `order_id` 유니크에 FAILED 재시도 정책을 더한다). 앱에서 `exists`로만 막으면 동시 요청이 몰릴 때 뚫리므로, DB 제약을 1차 방어선으로 둔다.
- 실패한 주문은 다시 결제할 수 있어야 한다. 그래서 새 Payment 생성을 허용하고, 유니크 대상도 활성 상태로만 한정한다.

### 금액

- 결제 금액은 주문의 `finalAmount`다. 쿠폰 할인까지 반영한 최종 금액으로, `docs/week4`에서 계산된다. 결제 API는 주문을 조회해 이 값을 서버에서 가져온다.

### 주문 상태 연계

- 주문은 결제 전에 `PENDING_PAYMENT` 상태로 생성되고, 이때 재고가 선점된다(주문 API가 따로 처리한다).
- 콜백이 `SUCCESS`면 주문을 `PAID`로 옮긴다. `FAILED`면 주문을 `PAYMENT_FAILED`로 옮기고 보상을 돌린다.
- 결제 API의 정상 흐름은 재고 락을 잡지 않는다. 재고 차감은 주문을 만들 때 이미 끝났고, 복구는 `FAILED` 콜백의 보상에서만 일어난다. PG가 응답하는 동안 락을 쥐고 있으면 커넥션이 고갈되므로, `docs/week4`의 동시성 원칙을 그대로 잇는다.

### 결제 분리가 주문 흐름에 미치는 영향

결제를 별도 API로 떼면서 주문 생성(`PlaceOrderFacade`)의 책임이 줄어든다.

- 주문 생성은 이제 재고를 선점하고 주문을 `PENDING_PAYMENT`로 만드는 데서 끝난다. 그 안에서 결제를 호출하지 않으니, `paymentService`·`orderCompensationService` 의존성과 결제 실패 시 보상하던 `try/catch`도 함께 빠진다.
- 그렇다고 `PlaceOrderFacade`가 사라지지는 않는다. 주문번호 채번은 자기만의 짧은 트랜잭션으로 먼저 커밋해 시퀀스 락을 곧바로 풀어야 하고, 그다음에 주문 생성 트랜잭션이 돈다. 이 두 트랜잭션을 순서대로 태우는 일은 단일 `@Transactional` 메서드로는 할 수 없어서, facade가 그 경계를 가르는 역할로 남는다. 이제는 여러 도메인을 엮는 facade가 아니라, 트랜잭션 경계를 가르는 facade인 셈이다.
- 보상(재고·쿠폰 복구)을 부르는 자리도 옮겨간다. 주문 생성이 아니라 결제가 실패했을 때만 돌아야 하므로, 진입점을 `PaymentResultHandler` 하나로 모은다. 정상 경로에서는 PG의 `FAILED` 콜백이 이 핸들러를 부르고, 콜백이 유실되면 `PaymentReconciler`가 같은 핸들러를 불러 보상을 마저 처리한다. 보상 로직이 두 갈래로 갈라지지 않게 하려는 것이다.

## 외부 연동 정책

### 트랜잭션 경계

PG 호출은 어떤 DB 트랜잭션에도 들어가지 않는다. 요청 흐름을 세 구간으로 끊는다.

1. **TX1** — `Payment`를 `PENDING`으로 생성하고(키와 provider는 아직 없음) 커밋한다. '결제를 시도했다'는 의도를 먼저 남기는 단계다.
2. **PG 호출** — 트랜잭션 밖에서 부른다. 성공하면 `transactionKey`와 `provider`를 받는다.
3. **TX2** — 받은 `transactionKey`와 `pgProvider`를 `Payment`에 채우고 커밋한 뒤, 클라이언트에 접수 응답을 내려준다.

콜백과 보상은 또 다른 트랜잭션(`TX_callback`)에서 처리한다.

> **왜 PENDING을 먼저 만드나.** TX1을 커밋한 뒤 PG를 호출하기 전에 죽으면, PG는 아무것도 모른다(돈이 움직이지 않았다). 그래서 안전하게 재처리하거나 정리할 수 있다. 반대로 PG를 먼저 호출하고 나서 레코드를 만들면, PG는 성공했는데 바로 다음 저장이 실패하는 순간 고아 거래가 된다 — PG에는 거래가 있는데 우리 쪽엔 기록이 없어 추적이 어렵다.

### Resilience4j (단일 PG 우선)

PG 호출에는 `CircuitBreaker( Retry( RateLimiter( call ) ) )` 순서로 데코레이터를 씌운다. 바깥에서 안쪽으로 CB → Retry → RateLimiter 순이다.

- **RateLimiter — 가장 안쪽.** PG별 호출 상한을 건다. 재시도로 늘어난 실제 호출 수까지 세야 상한이 의미가 있어서 가장 안쪽에 둔다.
- **Retry — 중간.** 전송 계층 실패에만 제한적으로 건다. PG에 안 닿은 게 확실한 실패(connect timeout, 연결 거부, 5xx를 받기 전)만 재시도한다. read timeout이나 응답 유실처럼 닿았을 수도 있는 모호한 실패는 재시도하지 않고 보정 작업으로 넘긴다. 400(파라미터 오류)은 재시도해 봐야 소용없으니 제외한다.
- **CircuitBreaker — 가장 바깥.** 장애가 이어지면 빠르게 차단한다. CB를 바깥에 둬야 두 가지가 성립한다. 하나, OPEN되면 retry 블록 전체를 즉시 건너뛴다. 둘, CB가 '논리적 결제 한 건의 성패'로 트립해서 PG 건강 상태를 정직하게 반영한다. Retry를 바깥에 두면 재시도로 부풀려진 실패까지 세어 과민하게 열리고, `CallNotPermitted`마저 재시도하는 문제가 생긴다.

### Failover (멀티 PG, +α)

CB가 OPEN이거나 RateLimiter가 거절하면 다른 PG로 우회한다. 단, failover는 요청이 PG-A에 안 나간 게 확실한 예외(`CallNotPermitted`, `RequestNotPermitted`)에서만 발동한다. PG에 일단 보내진 뒤 모호하게 실패한 경우에는 failover하지 않는다. 이중 결제 위험이 있어서, 이런 건은 orderId 기반 보정으로 처리한다.

```
try    callA()                                   // CircuitBreaker(Retry(RateLimiter(call)))
catch  CallNotPermitted | RequestNotPermitted →  callB()   // A에 안 나감 = 안전한 failover
catch  그 외(모호한 실패)                      →  throw → orderId 보정   // B로 보내지 않음
```

> 이 `catch`의 안전 분기는 '`CallNotPermitted`이면 A에 안 닿았다'는 불변식에 기댄다. 그리고 이 불변식은 CB가 바깥에 있을 때만 성립한다. 데코레이터 순서가 결제 정합성을 직접 좌우하는 이유가 이것이다.

### 정합성 보정

콜백을 보장할 수 없으므로, 어긋난 상태는 PG에 조회해서 맞춘다. 새로 결제를 요청하는 게 아니라 기존 거래를 조회해 보정하는 방식이다. PG는 orderId로 멱등하지 않아서, 재요청하면 그대로 이중 결제가 되기 때문이다. 규모상 `@Scheduled` 주기 작업으로 충분하다. 대량 처리나 재시작 보장이 필요해지기 전까지는 Spring Batch까지 갈 필요가 없다.

| sweep | 대상 | 조회 | 커버하는 장애 |
| --- | --- | --- | --- |
| 키 보유 sweep | `PENDING` + `transaction_key` 보유 + N초 경과 | `GET /payments/{key}` | 콜백 유실 |
| 키 없음 sweep | `PENDING` + `transaction_key IS NULL` + N초 경과 | `GET /payments?orderId=` | TX2 전 크래시(키 유실) |

## 장애 시나리오 & 대응

| 장애 | 남는 상태 | 대응 |
| --- | --- | --- |
| TX1 후 PG 호출 전 크래시 | Payment PENDING(키 없음), PG 모름 | 키 없음 sweep → PG에 거래 없음 확인 → 재처리/FAILED 정리 (돈 안 움직임, 안전) |
| PG 성공 ~ TX2 전 크래시 | Payment PENDING(키 없음), PG엔 거래 존재 | 키 없음 sweep(orderId) → 거래 매칭 → 키 채움/확정 |
| 콜백 유실 | Payment PENDING(키 보유), PG terminal | 키 보유 sweep(transactionKey) → 확정 |
| 콜백 처리 중 부분 실패 | (단일 TX 롤백) Payment PENDING 유지 | 보정 작업이 재처리(콜백·보정 공유 진입점) |
| 요청 응답 유실 → retry | (모호) | retry 대상에서 제외 + orderId 멱등으로 흡수 |

## 에러 코드 (`PaymentErrorCode`)

| 코드 | ErrorType | 상황 |
| --- | --- | --- |
| `PAYMENT_ORDER_NOT_FOUND` | NOT_FOUND | 결제 대상 주문이 없음 |
| `PAYMENT_ORDER_NOT_PAYABLE` | BAD_REQUEST | 주문이 `PENDING_PAYMENT`가 아님(이미 결제/취소) |
| `PAYMENT_ALREADY_IN_PROGRESS` | CONFLICT | 해당 주문에 활성 Payment가 이미 존재 |
| `PAYMENT_GATEWAY_UNAVAILABLE` | INTERNAL_ERROR | 모든 PG가 차단/불가(CB OPEN 등) |
| `PAYMENT_CALLBACK_INVALID` | BAD_REQUEST | 콜백의 transactionKey/orderId/amount 불일치 |

## 범위에서 제외

- **콜백 ACK 핸드셰이크.** 실제 PG는 콜백을 보낸 뒤 ACK까지 받아야 성공을 확정하지만, `pg-simulator`에는 ACK가 없어 이번 범위에서 뺀다.
- **멀티 PG 가중치 분배(라우팅).** RateLimiter는 상한만 걸 뿐, 비율로 나눠 보내는 건 별도 라우터의 몫이다. 이번 범위는 CB가 OPEN됐을 때 failover하는 데까지다. 가중치 라우팅은 +α로 남겨둔다.
- **Failover 실증.** 실제 PG-B가 있어야 검증에 의미가 있다. 우선 단일 PG에 CB·Retry·RateLimiter를 단단히 갖추고, gateway는 인터페이스로 열어둔다.
- **포인트 등 추가 결제수단.** 이번 범위는 PG 카드 결제다. 보상은 주문에 묶인 재고·쿠폰 복구를 따른다(`docs/week4`).
