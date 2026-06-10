# Plan: CPN-8 쿠폰 발급 내역 조회 (admin)

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

`GET /api-admin/v1/coupons/{couponId}/issues`로 관리자가 특정 템플릿의 발급 내역을 페이징 조회한다. 기존 `CouponAdminV1Controller`에 엔드포인트를 추가하고, 템플릿 활성 존재를 `CouponRepository.getActiveById`로 점검(404)한 뒤 `UserCouponRepository`에 템플릿별 발급 시각 내림차순 페이지 조회를 추가한다. 상태 판정은 CPN-7의 `getStatus`·`UserCouponStatus`를 재사용한다. 페이지 응답은 CPN-4 선례를 따른다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (CPN-7 골격 + CPN-4 페이지 패턴 확장)

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수
- [x] 관리자 인증 `AdminAuthInterceptor` 자동 적용(실패 403)
- [x] 템플릿 부재/삭제 → NOT_FOUND(`getActiveById`, CPN-2 재사용) — 삭제 템플릿 발급 내역 비노출(결정 4·CPN-5 정합)
- [x] 상태 판정 `getStatus(now)` 재사용(CPN-7), 기준 시각 `DateTimeUtil` 주입
- [x] 페이지 응답 구조 CPN-4 `PageResponse` 패턴
- [x] Info/DTO 필드 참조형 통일

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/coupon/CouponAdminV1Controller.java` (편집) — `@GetMapping("/{couponId}/issues")` → `readCouponIssues(@PathVariable Long couponId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size)`. `CouponFacade`·`DateTimeUtil` 주입(DateTimeUtil 신규 추가), `couponFacade.readCouponIssues(couponId, page, size, dateTimeUtil.now())`.
- `interfaces/api/coupon/CouponAdminV1Dto.java` (편집) — `IssueResponse(Long userCouponId, Long userId, UserCouponStatus status, ZonedDateTime issuedAt)` + `from(CouponIssueInfo)`, `IssuePageResponse(List<IssueResponse> content, int page, int size, long totalElements, int totalPages)` + `from(Page<CouponIssueInfo>)`.
- `interfaces/api/coupon/CouponAdminV1ApiSpec.java` (편집) — `@Operation`(발급 내역 조회) 추가.

### application
- `application/coupon/CouponFacade.java` (편집) — `@Transactional(readOnly = true) Page<CouponIssueInfo> readCouponIssues(Long couponId, int page, int size, ZonedDateTime now)`: `couponRepository.getActiveById(couponId)`(404) → `userCouponRepository.findByCouponIdOrderByCreatedAtDesc(couponId, page, size)` → `CouponIssueInfo.of(userCoupon, now)` 매핑.
- `application/coupon/CouponIssueInfo.java` (신규) — `record CouponIssueInfo(Long userCouponId, Long userId, UserCouponStatus status, ZonedDateTime issuedAt)` + `of(UserCouponModel, ZonedDateTime now)`(status = `getStatus(now)`, issuedAt = `getCreatedAt()`).

### domain
- `domain/coupon/UserCouponRepository.java` (편집) — `Page<UserCouponModel> findByCouponIdOrderByCreatedAtDesc(Long couponId, int page, int size)` 추가.

### infrastructure
- `infrastructure/coupon/UserCouponJpaRepository.java` (편집) — `Page<UserCouponModel> findByCouponIdOrderByCreatedAtDesc(Long couponId, Pageable pageable)`.
- `infrastructure/coupon/UserCouponRepositoryImpl.java` (편집) — `findByCouponIdOrderByCreatedAtDesc(couponId, PageRequest.of(page, size))` 위임.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 발급 내역도 `CouponFacade`에 편입 | 쿠폰 도메인 Facade 단일 응집(CPN-6·7과 일관) | 별도 Facade — 과분리 |
| 템플릿 존재를 `getActiveById`(엔티티 로드)로 점검 | 삭제 시 404 + CPN-2 재사용, 발급 내역 조회 전 단건이라 부담 적음 | `existsActiveById` — 신규 메서드 추가 잉여 |
