# Plan: CPN-7 내 쿠폰 목록 (상태 포함)

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

`GET /api/v1/users/me/coupons`로 로그인 회원이 본인 발급 쿠폰 전체를 상태와 함께 조회한다. `UserCoupon`에 상태 파생 질의 `getStatus(now)`와 `UserCouponStatus` enum을 신규 도입하고, `UserCouponRepository`에 회원별 발급 시각 내림차순 전체 조회를 추가한다. 페이지네이션 없이 리스트를 반환한다. UserLike의 user-scoped 조회(`/users/{userId}/likes`)와 달리 요구사항이 `me` 리터럴 경로라 신규 `UserCouponV1Controller`를 둔다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수
- [x] 회원 인증 `@LoginUser AuthenticatedUser`, 실패 시 UNAUTHENTICATED(401)
- [x] 상태는 저장 컬럼이 아닌 파생값 — `getStatus(now)`가 `usedAt`·`expiredAt`에서 판정(USED > EXPIRED > AVAILABLE)
- [x] 만료 기준 시각은 표현 계층 `DateTimeUtil` 주입
- [x] 페이지네이션 없음(1인 1매로 제한적) — 리스트 반환
- [x] 삭제된 템플릿 발급분 포함 — `user_coupons` 직접 조회라 자동 포함(템플릿 조인·필터 없음)
- [x] Info/DTO 필드 참조형 통일

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/coupon/UserCouponV1Controller.java` (신규) — `@RequestMapping("/api/v1/users/me/coupons")`, `GET` → `readMyCoupons(@LoginUser AuthenticatedUser loginUser)`. `CouponFacade`·`DateTimeUtil` 주입, `couponFacade.readMyCoupons(loginUser.userId(), dateTimeUtil.now())` 호출. (UserLike가 `LikeFacade`를 쓰듯 쿠폰 도메인 Facade 재사용)
- `interfaces/api/coupon/UserCouponV1Dto.java` (신규) — `MyCouponResponse(Long userCouponId, String name, DiscountType discountType, Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt, UserCouponStatus status)` + `from(UserCouponInfo)`. 응답은 `List<MyCouponResponse>` 직접 반환.
- `interfaces/api/coupon/UserCouponV1ApiSpec.java` (신규) — `@Tag` + `@Operation`(내 쿠폰 목록).

### application
- `application/coupon/CouponFacade.java` (편집) — `@Transactional(readOnly = true) List<UserCouponInfo> readMyCoupons(Long userId, ZonedDateTime now)`: `userCouponRepository.findByUserIdOrderByCreatedAtDesc(userId)` → 각 항목 `UserCouponInfo.of(userCoupon, now)` 매핑.
- `application/coupon/UserCouponInfo.java` (신규) — `record UserCouponInfo(Long userCouponId, String name, DiscountType discountType, Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt, UserCouponStatus status)` + `of(UserCouponModel, ZonedDateTime now)`(status = `userCoupon.getStatus(now)`).

### domain
- `domain/coupon/UserCouponModel.java` (편집) — `UserCouponStatus getStatus(ZonedDateTime now)`: `usedAt != null` → `USED`; 아니고 `expiredAt.isBefore(now)` → `EXPIRED`; 아니면 `AVAILABLE`(사용 완료 우선).
- `domain/coupon/UserCouponStatus.java` (신규 enum) — `AVAILABLE`·`USED`·`EXPIRED`.
- `domain/coupon/UserCouponRepository.java` (편집) — `List<UserCouponModel> findByUserIdOrderByCreatedAtDesc(Long userId)` 추가.

### infrastructure
- `infrastructure/coupon/UserCouponJpaRepository.java` (편집) — `List<UserCouponModel> findByUserIdOrderByCreatedAtDesc(Long userId)`.
- `infrastructure/coupon/UserCouponRepositoryImpl.java` (편집) — 위임.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `me` 리터럴 경로 + 신규 `UserCouponV1Controller` | 요구사항이 `/users/me/coupons`. 본인 전용이라 `{userId}` 검증 불필요 | UserLike식 `/users/{userId}/coupons` — userId 일치 검증이 더 붙음 |
| 상태를 `getStatus`로 파생(저장 안 함) | 만료는 시점 의존이라 컬럼 저장 시 정합 깨짐(결정 2) | `status` 컬럼 저장 — 만료 시각마다 갱신 필요 |
