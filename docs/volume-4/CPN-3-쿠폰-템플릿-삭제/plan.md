# Plan: CPN-3 쿠폰 템플릿 삭제

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

`DELETE /api-admin/v1/coupons/{couponId}`로 활성 쿠폰 템플릿을 soft delete한다. Brand 삭제 흐름(BRD-6)의 멱등 no-op 분기를 본뜨되, **cascade가 없다**(결정 4 — 발급 쿠폰은 스냅샷 독립). `CouponRepository.findActiveById`만 추가하고 `BaseEntity.delete()`를 재사용한다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검

- [x] 호출 방향 준수
- [x] soft delete = `BaseEntity.delete()` (volume-2 결정 7)
- [x] 멱등 — 부재·이미 삭제 시 no-op 정상 응답 (volume-2 결정 6)
- [x] cascade 없음 (결정 4 — BRD-6 상품 cascade와 의도적 차이)
- [x] 관리자 인증: `AdminAuthInterceptor` 자동 적용
- [x] 데이터 없는 200 = `ApiResponse.success()`

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/coupon/CouponAdminV1Controller.java` (편집) — `DELETE /{couponId}` → `deleteCoupon(@PathVariable Long couponId)` → `ApiResponse.success()` (`ApiResponse<Void>`)
- `interfaces/api/coupon/CouponAdminV1ApiSpec.java` (편집) — `@Operation` 삭제 항목 추가 (멱등 명시)

### application
- `application/coupon/CouponFacade.java` (편집) — `deleteCoupon(Long couponId)`: `findActiveById(couponId).ifPresent(CouponModel::delete)` (없으면 no-op)

### domain
- `domain/coupon/CouponModel.java` (재사용) — `BaseEntity.delete()` 그대로. 신규 행위 없음
- `domain/coupon/CouponRepository.java` (편집) — `Optional<CouponModel> findActiveById(Long id)` 추가

### infrastructure
- `infrastructure/coupon/CouponJpaRepository.java` (편집) — `findByIdAndDeletedAtIsNull` (CPN-2와 공유; 이미 있으면 재사용)
- `infrastructure/coupon/CouponRepositoryImpl.java` (편집) — `findActiveById`: `findByIdAndDeletedAtIsNull(id)` 그대로 반환

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| cascade 미도입 | 결정 4 — 발급 쿠폰은 스냅샷 독립이라 연쇄 처리 대상이 없음 | BRD-6식 cascade — 본 도메인엔 무효(발급 쿠폰 박탈은 범위 밖) |
