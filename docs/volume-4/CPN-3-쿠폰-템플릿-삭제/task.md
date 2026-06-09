# Task: CPN-3 쿠폰 템플릿 삭제

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수.

## Phase 1: 구현

- [X] T001 도메인+인프라: `CouponRepository.findActiveById` + `CouponRepositoryImpl.findActiveById`(`findByIdAndDeletedAtIsNull` 재사용) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/CouponRepository.java`, `.../infrastructure/coupon/CouponRepositoryImpl.java`
- [X] T002 통합 테스트: `findActiveById`(활성 present / 삭제·부재 empty) `apps/commerce-api/src/test/java/com/loopers/infrastructure/coupon/CouponRepositoryIntegrationTest.java`
- [X] T003 애플리케이션: `CouponFacade.deleteCoupon` (`findActiveById.ifPresent(delete)`) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponFacade.java`
- [X] T004 Facade 단위 테스트: `deleteCoupon`(활성 → delete 호출 / 미존재 → no-op) `apps/commerce-api/src/test/java/com/loopers/application/coupon/CouponFacadeTest.java`
- [X] T005 인터페이스: `CouponAdminV1Controller` DELETE + `CouponAdminV1ApiSpec` 항목(멱등 명시) `.../interfaces/api/coupon/`
- [X] T006 E2E 테스트: `DELETE /{couponId}`(200+SUCCESS·활성 조회 제외 / 반복 200 / 미존재 200 / 403) `apps/commerce-api/src/test/java/com/loopers/interfaces/api/CouponAdminV1ApiE2ETest.java`

## Phase 2: 마무리

- [X] T007 spec 테스트 계획 대비 누락 점검
- [X] T008 `.http`에 삭제 요청 케이스 추가 (정상·반복·미존재·인증누락) `http/commerce-api/coupon-admin-v1.http`
