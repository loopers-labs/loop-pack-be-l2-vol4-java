# Task: CPN-6 쿠폰 발급

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 발급 쿠폰(`UserCoupon`)은 신규 aggregate라 Foundational 페이즈를 둔다.

## Phase 1: Foundational (UserCoupon aggregate 골격 + Coupon.isExpired)

- [X] T001 도메인: `UserCouponModel` `@Entity`(`user_coupons`, UK `(user_id, coupon_id)`) + `private @Builder` + 정적 팩토리 `issue(Long userId, CouponModel coupon)`(스냅샷 복사) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponModel.java`
- [X] T002 Model 단위 테스트: `UserCouponModel.issue`(userId·couponId·이름·할인 타입·할인 값·최소 주문 금액·만료 시각 스냅샷 보유, 발급 직후 usedAt 없음) `apps/commerce-api/src/test/java/com/loopers/domain/coupon/UserCouponModelTest.java`
- [X] T003 도메인: `ExpiredAt.isExpired(now)`(`value.isBefore(now)`) + `CouponModel.isExpired(now)`(위임) `apps/commerce-api/src/main/java/com/loopers/domain/coupon/ExpiredAt.java`, `.../CouponModel.java`
- [X] T004 Model 단위 테스트: `CouponModel.isExpired`(만료 시각이 기준 이후·동일 false, 이전 true) `apps/commerce-api/src/test/java/com/loopers/domain/coupon/CouponModelTest.java`
- [X] T005 도메인+인프라: `UserCouponRepository`(save, existsByUserIdAndCouponId) + `UserCouponJpaRepository`(existsByUserIdAndCouponId) + `UserCouponRepositoryImpl` `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponRepository.java`, `.../infrastructure/coupon/UserCouponJpaRepository.java`, `.../infrastructure/coupon/UserCouponRepositoryImpl.java`
- [X] T006 통합 테스트: `UserCouponRepository`(발급 쿠폰 저장·조회, existsByUserIdAndCouponId 존재/부재, 최소 주문 금액 0 스냅샷 보존) `apps/commerce-api/src/test/java/com/loopers/infrastructure/coupon/UserCouponRepositoryIntegrationTest.java`

## Phase 2: 구현 (발급 유스케이스)

- [X] T007 애플리케이션: `UserCouponIssueInfo` record + `from(UserCouponModel)` `apps/commerce-api/src/main/java/com/loopers/application/coupon/UserCouponIssueInfo.java`
- [X] T008 애플리케이션: `CouponFacade.issueCoupon(userId, couponId, now)` 편입(회원 존재 `getActiveById`→`user.getId()` → 템플릿 활성 조회 → 만료 CONFLICT → 발급 이력 CONFLICT → 스냅샷 생성·저장) `apps/commerce-api/src/main/java/com/loopers/application/coupon/CouponFacade.java`
- [X] T009 Facade 단위 테스트: `CouponFacadeTest.IssueCoupon`(정상 발급·식별자 반환 / 템플릿 부재·삭제 NOT_FOUND / 만료 CONFLICT / 발급 이력 존재 CONFLICT) `apps/commerce-api/src/test/java/com/loopers/application/coupon/CouponFacadeTest.java`
- [X] T010 인터페이스: `CouponV1Dto.IssueResponse(userCouponId)` + `from(UserCouponIssueInfo)` `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/CouponV1Dto.java`
- [X] T011 인터페이스: `CouponV1Controller`(`POST /api/v1/coupons/{couponId}/issue`, `@LoginUser`, 201, `DateTimeUtil.now()` 주입) + `CouponV1ApiSpec` `apps/commerce-api/src/main/java/com/loopers/interfaces/api/coupon/CouponV1Controller.java`, `.../CouponV1ApiSpec.java`
- [X] T012 E2E 테스트: `POST /api/v1/coupons/{couponId}/issue`(201+userCouponId / 회원 인증 실패 401 / 템플릿 부재·삭제 404 / 만료 409 / 중복 발급 409) `apps/commerce-api/src/test/java/com/loopers/interfaces/api/CouponV1ApiE2ETest.java`

## Phase 3: 마무리

- [X] T013 설계 문서 정정: `docs/volume-4/04-erd.md` `user_coupons.min_order_amount` `NULL` → `NOT NULL`(erDiagram 주석 + DDL COMMENT)
- [X] T014 spec 테스트 계획 대비 누락 점검
- [X] T015 `.http`에 쿠폰 발급 요청 케이스 추가 `http/commerce-api/coupon-v1.http`
