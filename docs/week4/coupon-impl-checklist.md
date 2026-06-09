# 쿠폰 구현 체크리스트

## GET /api-admin/v1/coupons — 쿠폰 템플릿 목록 조회

- [x] `CouponTemplateJpaRepository` — JpaRepository 확장
- [x] `CouponTemplateRepositoryImpl` — domain Repository 구현
- [x] `CouponInfo` — CouponTemplate 정보 record
- [x] `CouponAdminV1ApiSpec` / `CouponAdminV1Controller` / `CouponAdminV1Dto` 생성
- [x] `GET /api-admin/v1/coupons?page=0&size=20` 응답: 페이지네이션 + 템플릿 목록

## GET /api-admin/v1/coupons/{couponId} — 쿠폰 템플릿 상세 조회

- [x] `CouponTemplateRepository.findById(Long id)` 확인
- [x] `GET /api-admin/v1/coupons/{couponId}` 응답: 템플릿 단건

## POST /api-admin/v1/coupons — 쿠폰 템플릿 등록

- [x] `CouponTemplateRepository.save()` 확인
- [x] `CouponAdminV1Dto` 등록 요청: `name`, `type(FIXED|RATE)`, `value`, `minOrderAmount`(선택), `expiredAt`
- [x] `POST /api-admin/v1/coupons` 응답: 생성된 템플릿

## PUT /api-admin/v1/coupons/{couponId} — 쿠폰 템플릿 수정

- [x] `CouponUpdateRequest` DTO — name, type, value, minOrderAmount, expiredAt
- [x] `CouponTemplateModel.update()` — CouponType, BigDecimal 파라미터로 내부에서 DiscountPolicy 조립
- [x] `CouponTemplateService.updateTemplate()` — CouponType, BigDecimal 파라미터로 위임
- [x] `PUT /api-admin/v1/coupons/{couponId}` 응답: 수정된 템플릿

## DELETE /api-admin/v1/coupons/{couponId} — 쿠폰 템플릿 삭제

- [x] `DELETE /api-admin/v1/coupons/{couponId}` 응답: 204 No Content

---

## GET /api-admin/v1/coupons/{couponId}/issues — 특정 쿠폰 발급 내역 조회

- [x] `CouponStatus` enum — AVAILABLE / USED / EXPIRED
- [x] `IssuedCouponModel` — `CouponStatus status`, `validateUsable(boolean templateExpired)`, `resolveStatus(boolean templateExpired)`
- [x] `IssuedCouponRepository` — `findAllByCouponTemplateId`, `findAllByUserId`
- [x] `IssuedCouponJpaRepository` — JPA 구현
- [x] `IssuedCouponRepositoryImpl` — domain Repository 구현
- [x] `IssuedCouponInfo` — 발급 쿠폰 정보 record
- [x] `GET /api-admin/v1/coupons/{couponId}/issues?page=0&size=20` 응답: 페이지네이션 + 발급 내역

---

## POST /api/v1/coupons/{couponId}/issue — 쿠폰 발급 요청

- [ ] `IssuedCouponService.getUsableCoupon(couponId, userId, templateExpired)` — 만료 체크
- [ ] `CouponFacade.issue(couponId, userId)` — 템플릿 존재 확인 → 발급 저장
- [ ] `CouponV1ApiSpec` / `CouponV1Controller` / `CouponV1Dto` 생성
- [ ] `POST /api/v1/coupons/{couponId}/issue` 응답: 발급된 쿠폰

## GET /api/v1/users/me/coupons — 내 쿠폰 목록 조회

- [ ] `IssuedCouponService.getMyIssuedCoupons(Long userId)` — 발급 목록 조회
- [ ] `CouponFacade.getMyIssuedCoupons(userId)` — 발급 목록 + 템플릿 만료 여부로 `resolveStatus()` 계산
- [ ] `UserV1Controller` — `GET /api/v1/users/me/coupons` 엔드포인트 추가
- [ ] `CouponV1Dto` 목록 응답: `couponId`, `name`, `type`, `value`, `minOrderAmount`, `expiredAt`, `status(AVAILABLE|USED|EXPIRED)`
