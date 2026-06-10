# Task: CPN-8 쿠폰 발급 내역 조회 (admin)

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. `UserCoupon` 골격·상태(`getStatus`·`UserCouponStatus`)는 CPN-7에서 완료 — Foundational 없음.

## Phase 1: 구현

- [X] T001 도메인+인프라: `UserCouponRepository.findByCouponIdOrderByCreatedAtDesc(couponId, page, size)` + `UserCouponJpaRepository`(Pageable) + `UserCouponRepositoryImpl` 위임 `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponRepository.java`, `.../infrastructure/coupon/`
- [X] T002 통합 테스트: 특정 템플릿 발급분만 발급 시각 내림차순 페이징(크기/오프셋)·총 개수·타 템플릿 제외 `apps/commerce-api/src/test/java/com/loopers/infrastructure/coupon/UserCouponRepositoryIntegrationTest.java`
- [X] T003 애플리케이션: `CouponIssueInfo` record + `of(UserCouponModel, now)`(status·issuedAt) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponIssueInfo.java`
- [X] T004 애플리케이션: `CouponFacade.readCouponIssues(couponId, page, size, now)`(readOnly, getActiveById 404 → 발급분 페이지 매핑) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponFacade.java`
- [X] T005 Facade 단위 테스트: `readCouponIssues`(템플릿 부재/삭제 NOT_FOUND, 정상 시 발급분 페이지를 상태 포함 Info로 매핑) `apps/commerce-api/src/test/java/com/loopers/application/coupon/CouponFacadeTest.java`
- [X] T006 인터페이스: `CouponAdminV1Dto.IssueResponse`/`IssuePageResponse` + `from` `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/CouponAdminV1Dto.java`
- [X] T007 인터페이스: `CouponAdminV1Controller` GET `/{couponId}/issues`(DateTimeUtil 주입) + `CouponAdminV1ApiSpec` 항목 추가 `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/`
- [X] T008 E2E 테스트: `GET /api-admin/v1/coupons/{couponId}/issues`(200+메타·항목 키·상태 / 빈 결과 200 / size 미지정 20 / 403 / 404 부재·삭제) `apps/commerce-api/src/test/java/com/loopers/interfaces/api/CouponAdminV1ApiE2ETest.java`

## Phase 2: 마무리

- [X] T009 spec 테스트 계획 대비 누락 점검
- [X] T010 `.http`에 발급 내역 조회 요청 케이스 추가 `http/commerce-api/coupon-admin-v1.http`
