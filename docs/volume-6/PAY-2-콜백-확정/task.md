# Task: PAY-2 콜백 확정

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. (신규 도메인 없음 → Foundational 페이즈 없음)

## Phase 1: 도메인 전이 (Payment 재사용 + Order 변경분)

- [X] T001 `OrderStatus`에 `PAID`·`PAYMENT_FAILED` 추가 `apps/commerce-api/src/main/java/com/loopers/domain/order/OrderStatus.java`
- [X] T002 `OrderModel`에 `markPaid()`·`markPaymentFailed()` 추가 `apps/commerce-api/src/main/java/com/loopers/domain/order/OrderModel.java`
- [X] T003 `OrderModel` 전이 단위 테스트 보강 `apps/commerce-api/src/test/java/com/loopers/domain/order/OrderModelTest.java` (`markPaid`: CREATED→PAID, `markPaymentFailed`: CREATED→PAYMENT_FAILED)
- [X] T004 `PaymentModel` 전이 단위 테스트 보강 `apps/commerce-api/src/test/java/com/loopers/domain/payment/PaymentModelTest.java` (`succeed`/`fail`이 PENDING에서만 전이, 종료 상태 무변경 멱등 가드, `fail` 사유 기록, `isTerminal`)

## Phase 2: 조회 (Repository)

- [X] T005 `PaymentRepository`에 `getByOrderId(Long)` 추가 `apps/commerce-api/src/main/java/com/loopers/domain/payment/PaymentRepository.java`
- [X] T006 `PaymentJpaRepository.findByOrderId` + `PaymentRepositoryImpl.getByOrderId`(부재 시 NOT_FOUND) `apps/commerce-api/src/main/java/com/loopers/infrastructure/payment/`
- [X] T007 `PaymentRepository.getByOrderId` 통합 테스트 보강 `apps/commerce-api/src/test/java/com/loopers/infrastructure/payment/PaymentRepositoryIntegrationTest.java` (조회 성공, 부재 시 NOT_FOUND)

## Phase 3: 유스케이스 (application → interfaces)

- [X] T008 `PaymentFacade.handleCallback(Long orderId, PaymentStatus result, String reason)` 추가 `apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentFacade.java` (조회→종료 시 멱등 무처리→성공/실패 분기로 결제·주문 동반 전이)
- [X] T009 `PaymentFacade.handleCallback` 단위 테스트 보강 `apps/commerce-api/src/test/java/com/loopers/application/payment/PaymentFacadeTest.java` (성공→SUCCESS+PAID, 실패→FAILED(사유)+PAYMENT_FAILED, 이미 종료→무변경·주문 미전이, 결제 없음→NOT_FOUND)
- [X] T010 `PaymentV1Dto.CallbackRequest` 추가 + `PaymentV1ApiSpec.handleCallback` + `PaymentV1Controller` 콜백 엔드포인트 `apps/commerce-api/src/main/java/com/loopers/interfaces/api/payment/` (`POST /api/v1/payments/callback`, 인증 없음, orderId 문자열→Long·status 문자열→PaymentStatus 경계 매핑)
- [X] T011 `PaymentV1ApiE2ETest` 콜백 케이스 보강 `apps/commerce-api/src/test/java/com/loopers/interfaces/api/PaymentV1ApiE2ETest.java` (성공 확정→주문 PAID, 실패 확정→주문 PAYMENT_FAILED, 중복 콜백 멱등, 결제 없음→404. status+meta.result+errorCode)

## Phase 4: 마무리

- [X] T012 spec 테스트 계획 대비 누락 점검 (Model/Service/Integration/E2E 매핑)
- [X] T013 `http/commerce-api/payment-v1.http`에 콜백 요청(성공/실패/중복/결제없음) 추가
