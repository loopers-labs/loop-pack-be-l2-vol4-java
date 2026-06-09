# Plan: CPN-2 쿠폰 템플릿 수정

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

`PUT /api-admin/v1/coupons/{couponId}`로 활성 쿠폰 템플릿을 수정한다. CPN-1에서 만든 Coupon aggregate에 `CouponModel.update`와 `CouponRepository.getActiveById`를 추가하고, Brand 수정 흐름(BRD-5: getActiveById → update → UpdateInfo)을 본뜬다. 이름 중복 검사가 없어 BRD-5보다 단순하다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (CPN-1 골격 확장)

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수
- [x] 검증은 VO/enum에 단일화 — `update`도 생성과 동일하게 `Name.from`·`DiscountType.validate`·`ExpiredAt.of`·`MinOrderAmount` 재사용. DTO는 null/blank 1차 방어만
- [x] discountType enum 수신 (CPN-1 review 계승), 허용 외 값은 advice가 400
- [x] 이름 중복 검사·409 없음 (CPN-1 결정 — 쿠폰 이름 중복 허용)
- [x] 부재/삭제 시 NOT_FOUND (`getActiveById`)
- [x] 관리자 인증: `AdminAuthInterceptor` 자동 적용

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/coupon/CouponAdminV1Controller.java` (편집) — `PUT /{couponId}` → `updateCoupon(@PathVariable Long couponId, @Valid @RequestBody UpdateRequest)`
- `interfaces/api/coupon/CouponAdminV1Dto.java` (편집) — `UpdateRequest`(name·discountType(enum)·discountValue·minOrderAmount·expiredAt, Create와 동일 검증 애너테이션), `UpdateResponse(Long couponId)` + `from(CouponUpdateInfo)`
- `interfaces/api/coupon/CouponAdminV1ApiSpec.java` (편집) — `@Operation` 수정 항목 추가

### application
- `application/coupon/CouponFacade.java` (편집) — `updateCoupon(couponId, name, DiscountType, discountValue, minOrderAmount, expiredAt)`: `getActiveById` → `coupon.update(...)` → `CouponUpdateInfo.from`
- `application/coupon/CouponUpdateInfo.java` (신규) — `record CouponUpdateInfo(Long couponId)` + `from(CouponModel)`

### domain
- `domain/coupon/CouponModel.java` (편집) — `update(String rawName, DiscountType type, Integer rawValue, Integer rawMinOrderAmount, ZonedDateTime rawExpiredAt, ZonedDateTime now)`: 생성자와 동일 로직(`Name.from`·`type.validate`·`discountValue=rawValue`·`MinOrderAmount.from`(null→0)·`ExpiredAt.of(rawExpiredAt, now)`)으로 자기 속성 갱신. `now`는 Controller가 `DateTimeUtil`로 주입
- `domain/coupon/CouponRepository.java` (편집) — `CouponModel getActiveById(Long id)` 추가

### infrastructure
- `infrastructure/coupon/CouponJpaRepository.java` (편집) — `Optional<CouponModel> findByIdAndDeletedAtIsNull(Long id)`
- `infrastructure/coupon/CouponRepositoryImpl.java` (편집) — `getActiveById`: `findByIdAndDeletedAtIsNull(id).orElseThrow(NOT_FOUND)`

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `update`가 생성과 동일 검증을 재실행 | 검증 단일 원천 유지 — 수정도 같은 불변식 보장 | 부분 검증 — 일부 필드만 검사하면 불변식 구멍 |
