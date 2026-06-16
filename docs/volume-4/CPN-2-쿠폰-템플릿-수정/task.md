# Task: CPN-2 쿠폰 템플릿 수정

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. (도메인 골격은 CPN-1에서 완료 — Foundational 없음)

## Phase 1: 구현

- [X] T001 도메인: `CouponModel.update(rawName, type, rawValue, rawMinOrderAmount, rawExpiredAt)` 추가 (생성자와 동일 검증 로직) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/CouponModel.java`
- [X] T002 Model 단위 테스트: `CouponModel.update`(필드 갱신, 정률 101·만료 과거 BAD_REQUEST) `apps/commerce-api/src/test/java/com/loopers/domain/coupon/CouponModelTest.java`
- [X] T003 도메인+인프라: `CouponRepository.getActiveById` + `CouponJpaRepository.findByIdAndDeletedAtIsNull` + `CouponRepositoryImpl.getActiveById`(NOT_FOUND) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/CouponRepository.java`, `.../infrastructure/coupon/`
- [X] T004 통합 테스트: `getActiveById`(활성 반환 / 삭제·부재 NOT_FOUND) `apps/commerce-api/src/test/java/com/loopers/infrastructure/coupon/CouponRepositoryIntegrationTest.java`
- [X] T005 애플리케이션: `CouponUpdateInfo` record + `from` `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponUpdateInfo.java`
- [X] T006 애플리케이션: `CouponFacade.updateCoupon` (getActiveById → update → Info) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponFacade.java`
- [X] T007 Facade 단위 테스트: `updateCoupon`(대상 부재/삭제 NOT_FOUND, 정상 갱신) `apps/commerce-api/src/test/java/com/loopers/application/coupon/CouponFacadeTest.java`
- [X] T008 인터페이스: `CouponAdminV1Dto.UpdateRequest`/`UpdateResponse` 추가 `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/CouponAdminV1Dto.java`
- [X] T009 인터페이스: `CouponAdminV1Controller` PUT + `CouponAdminV1ApiSpec` 항목 추가 `.../interfaces/api/coupon/`
- [X] T010 E2E 테스트: `PUT /{couponId}`(200+id·이름 갱신 / 동일 값 수정 200 / 최소금액 생략 수정 200 / 403 / 404 / 이름 101 400 / 정률 값 101 400 / 만료 과거 400) `apps/commerce-api/src/test/java/com/loopers/interfaces/api/CouponAdminV1ApiE2ETest.java`

## Phase 2: 마무리

- [X] T011 spec 테스트 계획 대비 누락 점검
- [X] T012 `.http`에 수정 요청 케이스 추가 `http/commerce-api/coupon-admin-v1.http`
