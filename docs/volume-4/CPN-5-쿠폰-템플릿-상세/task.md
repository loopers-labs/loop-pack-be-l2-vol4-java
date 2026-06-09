# Task: CPN-5 쿠폰 템플릿 상세 (admin)

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. (CPN-2 `getActiveById`·CPN-4 `CouponAdminInfo`/`DetailResponse` 재사용)

## Phase 1: 구현

- [X] T001 애플리케이션: `CouponFacade.readCoupon`(`@Transactional(readOnly=true)`, `getActiveById` → `CouponAdminInfo.from`) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponFacade.java`
- [X] T002 Facade 단위 테스트: `readCoupon`(활성 존재 시 정보 반환, 부재/삭제 NOT_FOUND) `apps/commerce-api/src/test/java/com/loopers/application/coupon/CouponFacadeTest.java`
- [X] T003 인터페이스: `CouponAdminV1Controller` GET `/{couponId}` + `CouponAdminV1ApiSpec` 항목 `.../interfaces/api/coupon/`
- [X] T004 E2E 테스트: `GET /{couponId}`(200+응답 키 / 만료 템플릿 200 / 403 / 404) `apps/commerce-api/src/test/java/com/loopers/interfaces/api/CouponAdminV1ApiE2ETest.java`

## Phase 2: 마무리

- [X] T005 spec 테스트 계획 대비 누락 점검 (활성 단건 조회 Integration은 CPN-2와 공유 — 중복 작성 안 함)
- [X] T006 `.http`에 상세 요청 케이스 추가 `http/commerce-api/coupon-admin-v1.http`
