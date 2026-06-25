# Plan: PAY-1 결제 요청

**Spec**: ./spec.md
**작성일**: 2026-06-25

## 요약

회원의 카드 결제 요청을 받아 본인/금액/멱등을 검증하고 `PENDING` 결제를 생성·저장한 뒤, 외부 결제 시스템(pg-simulator)에 접수를 요청해 거래 식별자를 기록한다. Payment 도메인 골격(Model·VO·Enum·Repository·외부 결제 시스템 포트)을 새로 만든다. **Stage 0 범위**라 타임아웃·재시도·서킷·fallback은 넣지 않고, FeignClient는 회복 어노테이션 없이 happy path 호출만 한다(외부 호출이 트랜잭션 안에 있는 것도 Stage 0의 의도된 상태).

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가:
  - `org.springframework.cloud:spring-cloud-starter-openfeign` (Spring Cloud BOM 2024.0.1 이미 관리됨) — `apps/commerce-api/build.gradle.kts`
  - `@EnableFeignClients` — `CommerceApiApplication`
  - pg-simulator base-url·callbackUrl 설정값 — `application.yml`(local 기준 `http://localhost:8082`, callbackUrl `http://localhost:8080`)

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수 (Order 도메인 패턴 그대로)
- [x] 검증은 VO `from()`에 단일화: `CardNo.from()`이 카드 번호 형식 검증의 단일 원천. DTO는 null/blank만 `@NotBlank`/`@NotNull` 1차 방어
- [x] 인증: `@LoginUser AuthenticatedUser loginUser` → `loginUser.userId()` (Order 컨트롤러와 동일)
- [x] 멱등(결정 1): 응용 계층 `existsByOrderId` 1차 방어 + `uk_payments_order_id` 유일 제약이 동시 요청의 두 번째 INSERT 차단 → `DataIntegrityViolationException`을 `CONFLICT`로 번역
- [x] 금액(결정 2): `OrderModel.getFinalAmount()`에서 도출, 요청 본문 금액 없음
- [x] 카드 종류(결정 3): 우리 `CardType` enum으로 받고, 어댑터가 외부 표현으로 매핑
- [x] 네이밍: Facade/Controller는 CRUD 컨벤션 `createPayment`. 외부 호출 포트는 도메인 의미 `requestPayment`(우리 CRUD 자원 조작이 아니라 외부 접수 요청)
- [x] VO 네이밍: 접두 없는 개념명 `CardNo`(패키지가 도메인 구분)
- [x] 정적 팩토리: 매개변수 1개 `from`, 여러 개 `of`

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/payment/PaymentV1Controller.java` — **`POST /api/v1/payments`**, `@ResponseStatus(CREATED)`, `createPayment(@Valid @RequestBody CreateRequest, @LoginUser AuthenticatedUser)` → `PaymentFacade.createPayment` 위임
- `interfaces/api/payment/PaymentV1Dto.java`
  - `CreateRequest(Long orderId, CardType cardType, String cardNo)` — `orderId`/`cardType` `@NotNull`, `cardNo` `@NotBlank` (형식 검증은 `CardNo.from`)
  - `PaymentResponse(Long paymentId, Long orderId, String status, String transactionKey)` — `from(PaymentInfo)`
- `interfaces/api/payment/PaymentV1ApiSpec.java` — SpringDoc 인터페이스 (Order 양식)

### application
- `application/payment/PaymentFacade.java` — `@Service @Transactional`, `createPayment(Long userId, Long orderId, CardType cardType, String cardNo)`:
  1. `orderRepository.getActiveByIdAndUserId(orderId, userId)` (없으면 NOT_FOUND — 기존 메서드 재사용)
  2. `paymentRepository.existsByOrderId(orderId)` true → `CONFLICT`
  3. `Payment.create(orderId, userId, order.getFinalAmount(), cardType, cardNo, now)` (CardNo.from 내부 검증)
  4. `paymentRepository.save(payment)` — 유일 제약 위반은 RepositoryImpl이 `CONFLICT`로 번역
  5. `paymentGateway.requestPayment(savedPayment)` → `transactionKey`
  6. `payment.recordTransactionKey(transactionKey)`
  7. `PaymentInfo.from(payment)` 반환
  - 시각 주입: `DateTimeUtil.now()`를 컨트롤러에서 전달(Order 패턴 동일)
- `application/payment/PaymentInfo.java` — `record PaymentInfo(Long paymentId, Long orderId, int amount, String status, String transactionKey)`, `from(PaymentModel)`

### domain
- `domain/payment/PaymentModel.java` — 엔티티(`BaseEntity` 상속)
  - `@Table(name="payments", uniqueConstraints={@UniqueConstraint(name="uk_payments_order_id", columnNames="order_id"), @UniqueConstraint(name="uk_payments_transaction_key", columnNames="transaction_key")})` — 04-erd의 두 유일 제약을 엔티티에 선언(ddl-auto create 시 생성, FR-009·통합테스트 근거). 쿠폰·좋아요 엔티티 선언 패턴과 동형
  - 필드: `orderId`(Long), `userId`(Long), `amount`(int), `cardType`(`@Enumerated(STRING)`), `cardNo`(`@Embedded CardNo`), `transactionKey`(String, nullable), `status`(`@Enumerated(STRING)`, default PENDING), `reason`(String, nullable), `requestedAt`(`ZonedDateTime`)
  - public `@Builder`(private 생성자) — `rawCardNo`를 받아 `CardNo.from`으로 검증하고 `PENDING`으로 시작(Order 엔티티 패턴 동일, 별도 정적 팩토리 없음)
  - `recordTransactionKey(String)` — 거래 식별자 기록
  - `succeed()` / `fail(String reason)` / `isPending()` / `isTerminal()` — PAY-2에서 사용(전이 가드). **PAY-1 범위에서는 create/recordTransactionKey만 실사용**하나, 도메인 골격이라 함께 정의(전이 메서드 단위 테스트는 PAY-2 cycle에서 보강)
- `domain/payment/CardNo.java` — `@Embeddable record CardNo(@Column(name="card_no") String value)`, `static from(String)` 형식 검증(`^\d{4}-\d{4}-\d{4}-\d{4}$`, null/blank), 위반 시 `BAD_REQUEST`
- `domain/payment/CardType.java` — `enum { SAMSUNG, KB, HYUNDAI }` (description 보유, Order/User enum 양식)
- `domain/payment/PaymentStatus.java` — `enum { PENDING, SUCCESS, FAILED }`
- `domain/payment/PaymentRepository.java` — `save(PaymentModel)`, `existsByOrderId(Long)` (PAY-1 범위. `findByOrderId`는 PAY-2에서 추가)
- `domain/payment/PaymentGateway.java` — 포트 인터페이스: `String requestPayment(PaymentModel payment)` (반환 = 거래 식별자). 인자를 분해하지 않고 도메인 엔티티를 그대로 전달, 어댑터가 필요한 필드를 추출(`userId`는 pg-simulator 필수 헤더 `X-USER-ID`에 매핑). 멀티 PG 염두의 경계만 정의, 조회 메서드는 폴링 단계에서 추가

### infrastructure
- `infrastructure/payment/PaymentRepositoryImpl.java` — `@Component implements PaymentRepository`, `save`는 `saveAndFlush`로 외부 호출 전 INSERT를 flush하고 `DataIntegrityViolationException`(유일 제약 위반)을 영속 경계에서 `CoreException(CONFLICT)`로 번역(Facade는 영속 예외 타입을 모름)
- `infrastructure/payment/PaymentJpaRepository.java` — `extends JpaRepository<PaymentModel, Long>`, `boolean existsByOrderId(Long)` (soft-delete 필터 없는 raw 검사 — `order_id`는 결제의 영속 닻이고 결제는 삭제되지 않으므로 raw UNIQUE `uk_payments_order_id`와 같은 기준으로 정렬. 다른 도메인의 `deletedAtIsNull` 관용구를 따르지 않는 의도된 예외)
- `infrastructure/payment/PaymentGatewayImpl.java` — `@Component implements PaymentGateway`:
  - `PgSimulatorClient` 호출, **`CardType` → 외부 표현 매핑**, **`orderId`(Long) → 6자리 이상 문자열 매핑**(`String.format("%06d", orderId)`), `callbackUrl`(config) 주입, 응답에서 `transactionKey` 추출
  - 회복 어노테이션 없음(Stage 0)
- `infrastructure/payment/PgSimulatorClient.java` — `@FeignClient(name="pg-simulator", url="${pg-simulator.base-url}")`, `POST /api/v1/payments` (헤더 `X-USER-ID`), 요청/응답 DTO는 pg-simulator 계약(`orderId` String, `amount` Long, `cardType`/`status` 외부 enum, `transactionKey`)
- `infrastructure/payment/PgSimulatorDto.java` — Feign 요청·응답 record(외부 계약 전용, 도메인 누출 금지)

### config / 실행
- `support/config/FeignConfig.java` — `@Configuration @EnableFeignClients(basePackages="com.loopers")` (Application에서 분리)
- `support/config/PropertiesConfig.java` — `@Configuration @ConfigurationPropertiesScan(basePackages="com.loopers")` (Application에서 분리)
- `apps/commerce-api/src/main/resources/application.yml` — `pg-simulator.base-url`(local `http://localhost:8082`), `pg-simulator.callback-url`(`http://localhost:8080/api/v1/payments/callback`)
- `http/commerce-api/payment-v1.http` — happy path 수동 검증

### ErrorType 매핑 요약
| 상황 | ErrorType |
|---|---|
| 주문 부재·타인 소유 | NOT_FOUND |
| 이미 결제 존재 / 유일 제약 위반 | CONFLICT |
| 카드 종류 미지원(enum 파싱 실패)·카드 번호 형식 위반 | BAD_REQUEST |

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `orderId` Long ↔ 6자리 zero-pad 문자열 어댑터 매핑 | pg-simulator가 `orderId.length >= 6`을 강제(`PaymentDto.validate`). 콜백도 같은 문자열로 되돌아오므로 어댑터에서 `%06d` 포맷 후 `Long.parseLong`로 역매핑 | Long 그대로 전송 → 시뮬레이터 BAD_REQUEST로 거부됨(불가) |
| `PaymentGateway` 포트 + `PgSimulatorClient`(Feign) 분리 | 도메인을 외부 HTTP 계약·외부 enum에서 격리(결정 3), 멀티 PG 경계 확보(TODO 결정). 어댑터가 매핑 전담 | Feign 인터페이스를 도메인이 직접 의존 → 외부 스펙이 도메인에 누출 |
| `succeed/fail/isPending/isTerminal`를 PAY-1에서 함께 정의 | Payment는 도메인 골격(foundational)이라 전이 메서드가 엔티티 응집의 일부. PAY-2가 즉시 사용 | PAY-2에서 추가 → 골격이 두 번 나뉘어 응집도 저하(단, 전이 단위 테스트는 PAY-2에서 작성) |
