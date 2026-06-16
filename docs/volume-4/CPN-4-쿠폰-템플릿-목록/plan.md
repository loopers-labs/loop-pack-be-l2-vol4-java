# Plan: CPN-4 쿠폰 템플릿 목록 (admin)

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

`GET /api-admin/v1/coupons?page&size`로 삭제되지 않은(만료 포함) 쿠폰 템플릿을 등록 시각 내림차순으로 페이징 조회한다. Brand 목록(BRD-2)의 `findActiveByPage` + `PageResponse` 패턴을 본뜨고, 템플릿 상세 출력 DTO `CouponAdminInfo`를 도입한다(CPN-5와 공유).

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검

- [x] 호출 방향 준수, 읽기 전용 트랜잭션(`@Transactional(readOnly=true)`)
- [x] 삭제 제외(soft delete) / 만료 포함(결정 2)
- [x] 페이지 기본값 page=0, size=20 — Controller `@RequestParam(defaultValue)`
- [x] 응답 `discountType`은 enum(문자열 직렬화), `minOrderAmount` null 허용
- [x] 페이지 응답 구조는 BRD-2 `PageResponse` 패턴 재사용
- [x] 엔티티 비노출 — Facade가 `CouponAdminInfo`로 변환

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/coupon/CouponAdminV1Controller.java` (편집) — `GET` → `readCoupons(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size)`
- `interfaces/api/coupon/CouponAdminV1Dto.java` (편집) — `DetailResponse`(couponId·name·discountType·discountValue·minOrderAmount·expiredAt·createdAt·updatedAt) + `from(CouponAdminInfo)`, `PageResponse`(content·page·size·totalElements·totalPages) + `from(Page<CouponAdminInfo>)`
- `interfaces/api/coupon/CouponAdminV1ApiSpec.java` (편집) — `@Operation` 목록 항목 추가

### application
- `application/coupon/CouponAdminInfo.java` (신규) — `record CouponAdminInfo(Long couponId, String name, DiscountType discountType, int discountValue, int minOrderAmount, ZonedDateTime expiredAt, ZonedDateTime createdAt, ZonedDateTime updatedAt)` + `from(CouponModel)` (minOrderAmount는 항상 non-null `int`=`MinOrderAmount.value()`(0=제약 없음), expiredAt은 `ExpiredAt.value()`)
- `application/coupon/CouponFacade.java` (편집) — `@Transactional(readOnly=true) readCoupons(int page, int size)`: `findActiveByPage` → `Page<CouponAdminInfo>` 매핑

### domain
- `domain/coupon/CouponRepository.java` (편집) — `Page<CouponModel> findActiveByPage(int page, int size)`

### infrastructure
- `infrastructure/coupon/CouponJpaRepository.java` (편집) — `Page<CouponModel> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable)`
- `infrastructure/coupon/CouponRepositoryImpl.java` (편집) — `findActiveByPage`: `findByDeletedAtIsNullOrderByCreatedAtDesc(PageRequest.of(page, size))`

## 복잡도 트래킹

(컨벤션·BRD-2 패턴 내. 추가 복잡도 없음)
