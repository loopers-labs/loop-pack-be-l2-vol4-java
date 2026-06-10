# Task: LOCK-2 쿠폰 사용 낙관적 락

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수.

## Phase 1: 구현

- [X] T001 `UserCouponModel`에 `@Version private Long version;` 필드 추가 `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponModel.java` — import `jakarta.persistence.Version`. 클래스 레벨 `@Builder`/`@AllArgsConstructor`와 공존(빌더에 노출되나 `issue`·fixture가 호출 안 하면 null → insert 시 0). `apply`·`getStatus`·`issue` 로직 변경 없음.
- [X] T002 `UserCouponRepository`에 `saveAndFlush(UserCouponModel)` 추가 + `UserCouponRepositoryImpl`에서 `userCouponJpaRepository.saveAndFlush` 위임 `apps/commerce-api/src/main/java/com/loopers/domain/coupon/UserCouponRepository.java`, `.../infrastructure/coupon/UserCouponRepositoryImpl.java`
- [X] T003 `OrderFacade.applyCoupon`에서 `apply` 직후 `userCouponRepository.saveAndFlush(userCoupon)` + `try/catch (OptimisticLockingFailureException)` → `CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.")` `apps/commerce-api/src/main/java/com/loopers/application/order/OrderFacade.java` (import `org.springframework.dao.OptimisticLockingFailureException`) — 수동 리뷰 반영: advice 핸들러 대신 응용 계층 번역

## Phase 2: 마무리

- [X] T004 검증: `ConcurrentCouponUse`(성공 1건 + 실패 주문 모두 `CoreException(CONFLICT)` 단언, #3) 통과 확인 — 전체 빌드 그린에 포함.
- [X] T005 회귀 점검: 쿠폰 적용 경로 단위/통합/E2E(`OrderFacadeTest.CreateOrderWithCoupon`·`UserCouponModelTest`·`OrderV1ApiE2ETest`·`CouponFacadeTest`)가 그린(`@Version` 추가가 단건 동작·fixture에 영향 없음 — fixture가 version 미지정 → insert 시 0).
- [X] T006 전체 검증: `./gradlew :apps:commerce-api:test` — 588개 전부 그린(동시성 2개 전환 + ApiControllerAdviceTest 추가).
