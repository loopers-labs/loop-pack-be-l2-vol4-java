# Task: ORD-7 주문 쿠폰 적용

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. Order 금액 모델 교체로 기존 주문 코드·테스트 동반 수정(Phase 2).

## Phase 1: 도메인·할인 계산

- [X] T001 도메인: `DiscountType.calculate(orderAmount, value)` 추상 메서드 + 상수 오버라이드(FIXED `min`, RATE 내림) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/DiscountType.java`
- [X] T002 Model 단위 테스트: `DiscountType.calculate`(정액 `min`·정률 내림 경계) `apps/commerce-api/src/test/java/com/loopers/domain/coupon/DiscountTypeTest.java`
- [X] T003 도메인: `UserCouponModel.isAvailable(now)`·`calculateDiscount(orderAmount)`(최소금액 미달 CONFLICT)·`use(now)` `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponModel.java`
- [X] T004 Model 단위 테스트: `isAvailable`(사용·만료 불가)·`calculateDiscount`(미달 CONFLICT·정액 캡·정률 내림)·`use`(usedAt 기록) `apps/commerce-api/src/test/java/com/loopers/domain/coupon/UserCouponModelTest.java`
- [X] T005 도메인+인프라: `UserCouponRepository.getActiveByIdAndUserId` + `UserCouponJpaRepository.findByIdAndUserIdAndDeletedAtIsNull` + `UserCouponRepositoryImpl`(NOT_FOUND) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponRepository.java`, `.../infrastructure/coupon/`
- [X] T006 통합 테스트: `getActiveByIdAndUserId`(본인 반환 / 타인·부재 NOT_FOUND) `apps/commerce-api/src/test/java/com/loopers/infrastructure/coupon/UserCouponRepositoryIntegrationTest.java`

## Phase 2: Order 금액 모델 교체 (기존 계약 변경)

- [X] T007 도메인: `OrderModel` `totalPrice` → `originalAmount`·`discountAmount`·`finalAmount`·`userCouponId`(`@Builder` 유지) `apps/commerce-api/src/main/java/com/loopers/domain/order/OrderModel.java`
- [X] T008 Model 단위 테스트: `OrderModelTest` 세 금액·userCouponId 반영 `apps/commerce-api/src/test/java/com/loopers/domain/order/OrderModelTest.java`
- [X] T009 애플리케이션: `OrderInfo`·`OrderAdminInfo`·`OrderAdminSummaryInfo` `totalPrice` → 세 금액(+admin userCouponId) `apps/commerce-api/src/main/java/com/loopers/application/order/`
- [X] T010 인터페이스: `OrderV1Dto`·`OrderAdminV1Dto` 응답 `totalPrice` → 세 금액(+userCouponId), `CreateRequest`에 `userCouponId` 추가 `apps/commerce-api/src/main/java/com/loopers/interfaces/api/order/`

## Phase 3: 주문 쿠폰 적용 유스케이스

- [X] T011 애플리케이션: `OrderFacade.createOrder(userId, items, userCouponId, now)`(재고 차감 → 쿠폰 소유·가용 검증 → 할인 계산 → use → 세 금액 build·저장) `apps/commerce-api/src/main/java/com/loopers/application/order/OrderFacade.java`
- [X] T012 인터페이스: `OrderV1Controller`가 `request.userCouponId()` 전달 + `OrderV1ApiSpec` 설명 갱신 `apps/commerce-api/src/main/java/com/loopers/interfaces/api/order/`
- [X] T013 Facade 단위 테스트: 쿠폰 적용 정상(USED 전이+재고 차감+세 금액) / 쿠폰 부재·타인 404 / 사용됨·만료 409 / 최소금액 미달 409 / 미적용 주문 / 재고 부족 시 쿠폰 미사용 `apps/commerce-api/src/test/java/com/loopers/application/order/OrderFacadeTest.java`
- [X] T014 통합 테스트: `OrderRepository` 세 금액·userCouponId 스냅샷 저장·재조회 보존 `apps/commerce-api/src/test/java/com/loopers/infrastructure/order/OrderRepositoryIntegrationTest.java`
- [X] T015 E2E 테스트: `POST /api/v1/orders` 쿠폰 적용 201(세 금액·쿠폰 식별자) / 미적용 201 / 401 / 쿠폰 부재·타인 404 / 사용됨·만료 409 / 최소금액 미달 409 / 재고 부족 전체 롤백(쿠폰 미사용·재고 원복) `apps/commerce-api/src/test/java/com/loopers/interfaces/api/OrderV1ApiE2ETest.java`
- [X] T016 기존 주문 테스트 동반 수정: `OrderAdminV1ApiE2ETest`·`OrderRepositoryIntegrationTest` 등 `totalPrice` 단언·픽스처 → 세 금액 `apps/commerce-api/src/test/java/com/loopers/...`

## Phase 4: 마무리

- [X] T017 설계 문서 정정: `02-sequence-diagrams.md` ORD-7 `OrderModel.of` → Builder, `03-class-diagram.md` Order 팩토리 → Builder·`UserCoupon.isOwnedBy` 제거
- [X] T018 spec 테스트 계획 대비 누락 점검
- [X] T019 `.http`에 쿠폰 적용 주문 케이스 추가 `http/commerce-api/order-v1.http`
