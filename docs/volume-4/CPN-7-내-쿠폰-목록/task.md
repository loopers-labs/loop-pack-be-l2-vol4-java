# Task: CPN-7 내 쿠폰 목록 (상태 포함)

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. `UserCoupon` aggregate 골격은 CPN-6에서 완료 — Foundational 없음.

## Phase 1: 구현

- [X] T001 도메인: `UserCouponStatus` enum(`AVAILABLE`·`USED`·`EXPIRED`) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponStatus.java`
- [X] T002 도메인: `UserCouponModel.getStatus(now)`(usedAt 우선 USED, 만료 EXPIRED, 아니면 AVAILABLE) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponModel.java`
- [X] T003 Model 단위 테스트: `getStatus`(미사용·미만료 AVAILABLE / 미사용·만료 EXPIRED / 사용·미만료 USED / 사용·만료 USED 우선) `apps/commerce-api/src/test/java/com/loopers/domain/coupon/UserCouponModelTest.java`
- [X] T004 도메인+인프라: `UserCouponRepository.findByUserIdOrderByCreatedAtDesc` + `UserCouponJpaRepository` 동명 메서드 + `UserCouponRepositoryImpl` 위임 `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponRepository.java`, `.../infrastructure/coupon/`
- [X] T005 통합 테스트: 회원 발급분 발급 시각 내림차순 조회·삭제 템플릿 발급분 포함·타 회원 제외 `apps/commerce-api/src/test/java/com/loopers/infrastructure/coupon/UserCouponRepositoryIntegrationTest.java`
- [X] T006 애플리케이션: `UserCouponInfo` record + `of(UserCouponModel, now)`(status 파생) `apps/commerce-api/src/main/java/com/loopers/application/coupon/UserCouponInfo.java`
- [X] T007 애플리케이션: `CouponFacade.readMyCoupons(userId, now)`(readOnly, 발급분→Info 매핑) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponFacade.java`
- [X] T008 Facade 단위 테스트: `readMyCoupons`(본인 발급분을 상태 포함 Info로 매핑) `apps/commerce-api/src/test/java/com/loopers/application/coupon/CouponFacadeTest.java`
- [X] T009 인터페이스: `UserCouponV1Dto.MyCouponResponse` + `from` `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/UserCouponV1Dto.java`
- [X] T010 인터페이스: `UserCouponV1Controller`(`GET /api/v1/users/me/coupons`, `@LoginUser`, `DateTimeUtil.now()`) + `UserCouponV1ApiSpec` `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/`
- [X] T011 E2E 테스트: `GET /api/v1/users/me/coupons`(200+항목 키·상태 / 빈 목록 200 / 401) `apps/commerce-api/src/test/java/com/loopers/interfaces/api/UserCouponV1ApiE2ETest.java`

## Phase 2: 마무리

- [X] T012 spec 테스트 계획 대비 누락 점검
- [X] T013 `.http`에 내 쿠폰 목록 요청 케이스 추가 `http/commerce-api/coupon-v1.http`
