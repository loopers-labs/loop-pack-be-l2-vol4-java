# Task: CPN-1 쿠폰 템플릿 등록

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수.

## Phase F: Foundational (Coupon 도메인 골격)

- [X] T001 `Name` VO 작성 (1~100자) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/Name.java`
- [X] T002 `ExpiredAt` VO 작성 (null·과거 금지) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/ExpiredAt.java` *(review B: 당초 `DiscountValue` VO 대체)*
- [X] T003 `MinOrderAmount` VO 작성 (≥1, nullable column) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/MinOrderAmount.java`
- [X] T004 `DiscountType` enum 작성 (template method: `validate(Integer)` null 가드 + abstract `validateRange(int)`; FIXED ≥1, RATE 1~100) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/DiscountType.java`
- [X] T005 `CouponModel` 작성 (`@Builder`, VO 조립 + `type.validate` + `ExpiredAt.of`; `discountValue`는 원시 int) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/CouponModel.java`
- [X] T006 `CouponRepository` 인터페이스 (`save`) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/CouponRepository.java`
- [X] T007 `CouponJpaRepository` + `CouponRepositoryImpl` `apps/commerce-api/src/main/java/com/loopers/infrastructure/coupon/`

## Phase 1: 구현

- [X] T008 VO 단위 테스트: `Name`(1·100 통과, blank·101 BAD_REQUEST) `apps/commerce-api/src/test/java/com/loopers/domain/coupon/NameTest.java`
- [X] T009 VO 단위 테스트: `ExpiredAt`(미래 통과, null·과거 BAD_REQUEST) `.../domain/coupon/ExpiredAtTest.java` *(review B: 당초 `DiscountValueTest` 대체)*
- [X] T010 VO 단위 테스트: `MinOrderAmount`(1 통과, 0 BAD_REQUEST) `.../domain/coupon/MinOrderAmountTest.java`
- [X] T011 enum 단위 테스트: `DiscountType.validate`(null·정액<1·정률 0/101 BAD_REQUEST, 정액 ≥1·정률 1/100 통과) `.../domain/coupon/DiscountTypeTest.java`
- [X] T012 Model 단위 테스트: `CouponModel`(생성 시 필드 보유, minOrderAmount null 허용, 정률 값 101 BAD_REQUEST, 만료 시각 과거 BAD_REQUEST) `.../domain/coupon/CouponModelTest.java`
- [X] T013 애플리케이션: `CouponCreateInfo` record + `from` `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponCreateInfo.java`
- [X] T014 애플리케이션: `CouponFacade.createCoupon` (`DiscountType` enum 수신 → `CouponModel.builder` → save → Info) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponFacade.java`
- [X] T015 Facade 단위 테스트: `CouponFacade`(Mockito `CouponRepository` — 정상 생성 시 save 호출 + 비-null Info, 할인 값 범위 밖 BAD_REQUEST·미저장) `apps/commerce-api/src/test/java/com/loopers/application/coupon/CouponFacadeTest.java`
- [X] T016 인터페이스: `CouponAdminV1Dto`(`CreateRequest`는 `DiscountType` enum 수신·`@NotNull`/`CreateResponse`) `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/CouponAdminV1Dto.java`
- [X] T017 인터페이스: `CouponAdminV1ApiSpec` `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/CouponAdminV1ApiSpec.java`
- [X] T018 인터페이스: `CouponAdminV1Controller`(`POST`, `@ResponseStatus(CREATED)`) `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/CouponAdminV1Controller.java`
- [X] T019 통합 테스트: `CouponRepository` 저장·조회 `apps/commerce-api/src/test/java/com/loopers/infrastructure/coupon/CouponRepositoryIntegrationTest.java`
- [X] T020 E2E 테스트: `POST /api-admin/v1/coupons`(201+SUCCESS+id / 최소금액 생략 201 / admin 실패 403 / 알 수 없는 타입 400 / 이름 101 400 / 정률 값 101 400 / 만료 과거 400) `apps/commerce-api/src/test/java/com/loopers/interfaces/api/CouponAdminV1ApiE2ETest.java`

## Phase 2: 마무리

- [X] T021 spec 테스트 계획 대비 누락 점검 (VO/Model·Facade·Integration·E2E 모두 매핑되는지)
- [X] T022 `.http` 파일 작성 (`brand-admin-v1.http` 컨벤션 따라 등록 요청 + 실패 케이스) `http/commerce-api/coupon-admin-v1.http`
