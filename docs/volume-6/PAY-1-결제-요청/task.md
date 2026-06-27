# Task: PAY-1 결제 요청

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수.

## Phase F: Foundational (Payment 도메인 골격)

- [X] T001 `CardType` enum 작성 `apps/commerce-api/src/main/java/com/loopers/domain/payment/CardType.java` (SAMSUNG/KB/HYUNDAI, description)
- [X] T002 `PaymentStatus` enum 작성 `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentStatus.java` (PENDING/SUCCESS/FAILED, description)
- [X] T003 `CardNo` VO 작성 `apps/commerce-api/src/main/java/com/loopers/domain/payment/CardNo.java` (`@Embeddable record`, `from(String)` 형식 검증 `^\d{4}-\d{4}-\d{4}-\d{4}$`, null/blank → BAD_REQUEST)
- [X] T004 `CardNo` 단위 테스트 작성 `apps/commerce-api/src/test/java/com/loopers/domain/payment/CardNoTest.java` (형식 통과/거부 경계값, 위반 ErrorType)
- [X] T005 `PaymentModel` 엔티티 작성 `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentModel.java` (필드·`create`·`recordTransactionKey`·`succeed`·`fail`·`isPending`·`isTerminal`, BaseEntity 상속, `@Table(name="payments", uniqueConstraints={uk_payments_order_id=order_id, uk_payments_transaction_key=transaction_key})` 두 유일 제약 선언)
- [X] T006 `PaymentModel` 단위 테스트 작성 `apps/commerce-api/src/test/java/com/loopers/domain/payment/PaymentModelTest.java` (`create`가 PENDING·requestedAt·transactionKey 부재로 시작, `recordTransactionKey` 기록) — 전이 메서드 단언은 PAY-2
- [X] T007 `PaymentRepository` 인터페이스 작성 `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentRepository.java` (`save`, `existsByOrderId`)
- [X] T008 `PaymentJpaRepository` + `PaymentRepositoryImpl` 작성 `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/` (`existsByOrderIdAndDeletedAtIsNull`)
- [X] T009 `PaymentRepository` 통합 테스트 작성 `apps/commerce-api/src/test/java/com/loopers/infrastructure/payment/PaymentRepositoryIntegrationTest.java` (영속화, `uk_payments_order_id` 유일 제약 위반, 주문별 존재 검사)
- [X] T010 `PaymentGateway` 포트 인터페이스 작성 `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentGateway.java` (`requestPayment(orderId, amount, cardType, cardNo) → transactionKey`)

## Phase 1: 외부 연동 어댑터 (Feign)

- [X] T011 Feign 의존성 추가 `apps/commerce-api/build.gradle.kts` (`spring-cloud-starter-openfeign`) + `@EnableFeignClients` `apps/commerce-api/src/main/java/com/loopers/CommerceApiApplication.java`
- [X] T012 pg-simulator 설정값 추가 `apps/commerce-api/src/main/resources/application.yml` (`pg-simulator.base-url` local `http://localhost:8082`, `pg-simulator.callback-url` `http://localhost:8080/api/v1/payments/callback`)
- [X] T013 `PgSimulatorDto` + `PgSimulatorClient`(@FeignClient) 작성 `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/` (`POST /api/v1/payments`, `X-USER-ID` 헤더, 외부 계약 record)
- [X] T014 `PaymentGatewayImpl` 작성 `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/PaymentGatewayImpl.java` (`CardType` 외부 매핑, `orderId` `%06d` 매핑, `callbackUrl` 주입, `transactionKey` 추출. 회복 어노테이션 없음)

## Phase 2: 유스케이스 (application → interfaces)

- [X] T015 `PaymentInfo` 작성 `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentInfo.java` (`from(PaymentModel)`)
- [X] T016 `PaymentFacade` 작성 `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentFacade.java` (`createPayment`: 주문 본인 검증 → 멱등 검사 → PENDING 생성·저장(제약 위반 CONFLICT 번역) → 외부 접수 요청 → transactionKey 기록)
- [X] T017 `PaymentFacade` 단위 테스트 작성 `apps/commerce-api/src/test/java/com/loopers/application/payment/PaymentFacadeTest.java` (주문 부재/타인→NOT_FOUND, 기존 결제→CONFLICT, 금액 주문 도출, 외부 접수·키 기록 협력)
- [X] T018 `PaymentV1Dto` + `PaymentV1ApiSpec` + `PaymentV1Controller` 작성 `apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/` (`POST /api/v1/payments`, `@LoginUser`, `@ResponseStatus(CREATED)`)
- [X] T019 `PaymentV1ApiE2ETest` 작성 `apps/commerce-api/src/test/java/com/loopers/interfaces/api/payment/PaymentV1ApiE2ETest.java` (성공: status+meta.result+응답 키. 실패: NOT_FOUND·CONFLICT·BAD_REQUEST의 status+meta.result+errorCode. 외부 호출은 `@MockitoBean PaymentGateway`로 대체)

## Phase 3: 마무리

- [X] T020 spec 테스트 계획 대비 누락 점검 (VO/Model/Service/Integration/E2E 매핑 확인)
- [X] T021 `http/commerce-api/payment-v1.http` 작성 (happy path 수동 검증용)
