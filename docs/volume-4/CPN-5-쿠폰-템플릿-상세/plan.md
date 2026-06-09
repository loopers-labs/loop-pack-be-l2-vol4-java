# Plan: CPN-5 쿠폰 템플릿 상세 (admin)

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

`GET /api-admin/v1/coupons/{couponId}`로 활성 쿠폰 템플릿 상세를 조회한다. CPN-2의 `getActiveById`와 CPN-4의 `CouponAdminInfo`·`DetailResponse`를 재사용하므로 신규 도메인·인프라 자산이 거의 없다. Brand 상세(BRD-3) 패턴 동형.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (CPN-2·CPN-4 자산 재사용)

## 컨벤션·결정 점검

- [x] 호출 방향 준수, 읽기 전용 트랜잭션
- [x] 활성만 노출 / 만료 포함(결정 2) / 부재·삭제 404 (`getActiveById`)
- [x] 응답 `discountType` enum, `minOrderAmount` null 허용
- [x] 관리자 인증 자동 적용

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/coupon/CouponAdminV1Controller.java` (편집) — `GET /{couponId}` → `readCoupon(@PathVariable Long couponId)` → `DetailResponse.from(info)`
- `interfaces/api/coupon/CouponAdminV1Dto.java` (재사용) — CPN-4의 `DetailResponse`
- `interfaces/api/coupon/CouponAdminV1ApiSpec.java` (편집) — `@Operation` 상세 항목 추가

### application
- `application/coupon/CouponFacade.java` (편집) — `@Transactional(readOnly=true) readCoupon(Long couponId)`: `getActiveById` → `CouponAdminInfo.from`
- `application/coupon/CouponAdminInfo.java` (재사용) — CPN-4 도입

### domain
- `domain/coupon/CouponRepository.java` (재사용) — CPN-2의 `getActiveById`

### infrastructure
- (재사용) — CPN-2의 `findByIdAndDeletedAtIsNull` / `getActiveById`

## 복잡도 트래킹

(신규 자산 거의 없음 — CPN-2·4 재사용. 추가 복잡도 없음)
