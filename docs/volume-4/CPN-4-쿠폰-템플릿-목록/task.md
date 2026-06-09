# Task: CPN-4 쿠폰 템플릿 목록 (admin)

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수.

## Phase 1: 구현

- [X] T001 도메인+인프라: `CouponRepository.findActiveByPage` + `CouponJpaRepository.findByDeletedAtIsNullOrderByCreatedAtDesc` + `CouponRepositoryImpl.findActiveByPage` `apps/commerce-api/src/main/java/com/loopers/domain/coupon/CouponRepository.java`, `.../infrastructure/coupon/`
- [X] T002 통합 테스트: `findActiveByPage`(삭제 제외·만료 포함·등록 시각 내림차순·페이징/오프셋·총 개수) `apps/commerce-api/src/test/java/com/loopers/infrastructure/coupon/CouponRepositoryIntegrationTest.java`
- [X] T003 애플리케이션: `CouponAdminInfo` record + `from(CouponModel)` (minOrderAmount null 처리, expiredAt `.value()`) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponAdminInfo.java`
- [X] T004 애플리케이션: `CouponFacade.readCoupons`(`@Transactional(readOnly=true)`, Page 매핑) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponFacade.java`
- [X] T005 Facade 단위 테스트: `readCoupons`(활성 페이지를 `CouponAdminInfo`로 매핑 반환; `BrandFacadeTest.ReadBrands` 스타일) `apps/commerce-api/src/test/java/com/loopers/application/coupon/CouponFacadeTest.java`
- [X] T006 인터페이스: `CouponAdminV1Dto.DetailResponse` + `PageResponse` 추가 `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/CouponAdminV1Dto.java`
- [X] T007 인터페이스: `CouponAdminV1Controller` GET(page·size) + `CouponAdminV1ApiSpec` 항목 `.../interfaces/api/coupon/`
- [X] T008 E2E 테스트: `GET ?page&size`(200+메타·항목 키 / 빈 결과 200 / size 미지정 20 / 만료 포함 / 403) `apps/commerce-api/src/test/java/com/loopers/interfaces/api/CouponAdminV1ApiE2ETest.java`

## Phase 2: 마무리

- [X] T009 spec 테스트 계획 대비 누락 점검
- [X] T010 `.http`에 목록 요청 케이스 추가 `http/commerce-api/coupon-admin-v1.http`
