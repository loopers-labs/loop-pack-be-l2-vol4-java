# Plan: PAY-2 콜백 확정

**Spec**: ./spec.md
**작성일**: 2026-06-25

## 요약

외부 결제 시스템이 `callbackUrl`로 POST하는 처리 결과를 받아, 주문 식별자로 결제를 조회해 `PENDING`을 `SUCCESS`/`FAILED`로 확정하고 주문을 `PAID`/`PAYMENT_FAILED`로 동반 전이한다. 신규 도메인 없이 **PAY-1에서 만든 Payment의 전이 메서드를 실사용**하고, **Order에 결제 결과 전이를 추가**하며, 콜백 엔드포인트를 더한다. Stage 0 범위라 콜백 교차검증·폴링·동시성 제어는 넣지 않는다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 추가 의존성: 없음 (PAY-1의 Feign·설정 재사용)

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수
- [x] 콜백 식별 기준 = 주문 식별자(결정 1). 거래 식별자는 조회 기준이 아님
- [x] 멱등: 결제 종료 상태 불변(`isTerminal` 가드)로 중복 콜백 후처리 1회. 콜백·폴링 **동시** 경합은 Stage 7(조건부 UPDATE) 범위 밖
- [x] 인증: 콜백은 PG 시스템 호출이라 `@LoginUser` 미적용(공개 엔드포인트)
- [x] 인바운드 경계 매핑: 콜백 본문 `orderId`(6자리 문자열) → 우리 `Long`으로 역매핑(PAY-1 아웃바운드 `%06d`의 대칭). 외부 처리 상태 문자열 → 우리 `PaymentStatus`로 매핑(결정 3과 동형, 경계에서만 변환)
- [x] 네이밍: Controller/Facade 콜백 처리 메서드는 `handleCallback`(CRUD 자원 조작이 아닌 외부 통보 수신). 조회는 `getByOrderId`(get*=부재 시 NOT_FOUND, OrderRepository.getActiveById 동형)
- [x] soft-delete 정렬: 결제 조회도 PAY-1 `existsByOrderId`와 같은 raw 기준(결제는 영속 닻이라 삭제되지 않음)

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/payment/PaymentV1Controller.java` (수정) — **`POST /api/v1/payments/callback`** 추가, `@ResponseStatus(OK)`, `handleCallback(@Valid @RequestBody CallbackRequest)` → `PaymentFacade.handleCallback(orderId, transactionKey, status, reason)` 위임. 회원 인증 없음(PG 호출), 대신 거래 식별자 진위 검증은 Facade가 수행
- `interfaces/api/payment/PaymentV1Dto.java` (수정) — `CallbackRequest(@NotNull Long orderId, @NotBlank String transactionKey, @NotNull PaymentStatus status, String reason)` 추가. `transactionKey`는 콜백 진위 검증(위조 방어)에 사용. `orderId`는 `Long`이라 Jackson이 숫자 강제(비숫자는 400), `status`는 `PaymentStatus` enum 바인딩(미지원 값 `READ_UNKNOWN_ENUM_VALUES_AS_NULL` disable로 400). pg-simulator의 나머지 페이로드(`cardType/cardNo/amount`)는 `FAIL_ON_UNKNOWN_PROPERTIES` disable로 무시. 응답 본문 없음(`ApiResponse<Void>`)
- `interfaces/api/payment/PaymentV1ApiSpec.java` (수정) — `handleCallback` 오퍼레이션 추가

### application
- `application/payment/PaymentFacade.java` (수정) — `@Transactional handleCallback(Long orderId, String transactionKey, PaymentStatus result, String reason)`:
  1. `paymentRepository.getByOrderId(orderId)` (없으면 NOT_FOUND)
  2. `payment.matchesTransactionKey(transactionKey)` 아니면 FORBIDDEN (콜백 위조 방어 — 접수 때 기록한 거래 식별자와 일치해야 확정)
  3. `payment.isTerminal()` 이면 무처리 반환(멱등)
  4. `payment.confirm(result, reason)` — 결제가 콜백 결과로 자기 전이를 캡슐화(SUCCESS→succeed/FAILED→fail/그 외 PENDING→무처리)
  5. 확정된 결제 상태로 주문 전이 결정: `payment.isSuccess()` → `orderRepository.getActiveById(orderId).markPaid()` / `payment.isFailed()` → `markPaymentFailed()`. 주문 전이 분기를 Order에 넣으면 Aggregate 간 타입 결합이 생기므로 응용 계층이 번역
  - 변경은 `@Transactional` 더티체킹으로 커밋 시 반영

### domain
- `domain/payment/PaymentRepository.java` (수정) — `PaymentModel getByOrderId(Long orderId)` 추가
- `domain/payment/PaymentModel.java` (수정) — 콜백 결과로 자기 전이를 캡슐화하는 `confirm(PaymentStatus result, String reason)` 추가, 주문 동반 전이 판단용 `isSuccess()`/`isFailed()` 추가(`isTerminal`은 이 둘로 재정의), 콜백 진위 검증용 `matchesTransactionKey(String)` 추가(기록된 키가 있고 같을 때만 참). PAY-1의 `succeed`/`fail` 재사용. 전이·일치 단위 테스트를 본 cycle에서 보강
- `domain/order/OrderStatus.java` (수정) — `PAID`("결제 완료")·`PAYMENT_FAILED`("결제 실패") 추가
- `domain/order/OrderModel.java` (수정) — `markPaid()`(상태 `PAID`)·`markPaymentFailed()`(상태 `PAYMENT_FAILED`) 추가. 단일 전이는 응용 계층의 결제 종료 가드가 1회로 통제하므로 단순 전이로 둔다

### infrastructure
- `infrastructure/payment/PaymentJpaRepository.java` (수정) — `Optional<PaymentModel> findByOrderId(Long orderId)` 추가(raw)
- `infrastructure/payment/PaymentRepositoryImpl.java` (수정) — `getByOrderId` 구현: `findByOrderId(...).orElseThrow(() -> CoreException(NOT_FOUND))`

### config / 실행
- `http/commerce-api/payment-v1.http` (수정) — 콜백 성공/실패/중복/결제없음 요청 추가

### ErrorType 매핑 요약
| 상황 | ErrorType |
|---|---|
| 콜백이 가리키는 결제 부재 | NOT_FOUND |
| 콜백 거래 식별자 불일치(위조) | FORBIDDEN |
| 입력 형식 위반(비숫자 orderId·미지원 status·빈 transactionKey) | BAD_REQUEST |
| 정상(성공·실패·중복 멱등) | 200 OK |

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 콜백 `status`를 `PaymentStatus` enum으로 받아 Facade에 전달 | 외부 처리 상태(PENDING/SUCCESS/FAILED)와 우리 `PaymentStatus`가 동형. Jackson이 인바운드 경계에서 string→enum 바인딩(미지원 값 400) → 컨트롤러의 수동 `valueOf` 제거 | 별도 콜백 결과 enum 신설 → 동형 enum 중복 / status를 String으로 받아 컨트롤러에서 `valueOf` → 미지원 값 500 |
| 결제 전이를 `payment.confirm()`으로 캡슐화하고 Facade는 결제의 확정 상태로 주문 전이 결정 | 결제가 자기 상태 전이의 주인(tell-don't-ask), Facade는 두 Aggregate 오케스트레이션만. 주문 분기를 결제의 원천 상태 기준으로 | 주문 전이 분기를 Order에 `reflect(PaymentStatus)`로 넣기 → Order가 Payment enum에 결합 |
| 콜백 진위를 거래 식별자 일치로 검증(Stage 0 선반영) | 공개 콜백 엔드포인트에서 임의 주문 결과 위조를 막는 경량 방어. 우리가 이미 저장하고 시뮬레이터도 보내는 값이라 비용 0 | 아무 검증 없이 본문 신뢰 → 누구나 SUCCESS 위조 / 완전한 HMAC·replay·PG 조회 → 시뮬레이터가 서명을 안 줘 불가, Stage 6 |
| 콜백 `orderId` 문자열을 컨트롤러에서 `Long` 파싱 | PAY-1 아웃바운드 `%06d`의 대칭 역매핑. 콜백 `orderId`는 항상 우리가 보낸 형식 | 도메인까지 문자열 전파 → 경계 책임 누수 |
| `transactionKey`는 Facade 시그니처에서 제외 | Stage 0 확정은 주문 식별자 조회로 충분. 거래 식별자 교차검증은 Stage 6 | 미사용 인자 보유 → YAGNI |
| Order 전이를 가드 없는 단순 전이로 | 멱등(중복 콜백 1회 후처리)은 결제 종료 상태 가드가 단일 통제. Order는 그 1회에만 전이 | Order에도 상태 가드 추가 → 이중 가드로 복잡, 03-class 설계와 불일치 |

## 가정 / 범위 밖

- 콜백 본문 `orderId`는 우리가 보낸 숫자 형식이라 파싱 실패를 가정하지 않는다(Stage 0). 처리 상태 문자열도 PG 계약값(SUCCESS/FAILED/PENDING)을 전제한다.
- 콜백 본문 PG 교차검증·서명/인증(Stage 6·범위 밖), 콜백 누락 폴링 보정(Stage 6), 콜백·폴링 동시 확정 경합 제어(Stage 7), 재고·쿠폰 보상 복원.
