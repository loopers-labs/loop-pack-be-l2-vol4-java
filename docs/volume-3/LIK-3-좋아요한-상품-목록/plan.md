# Plan: LIK-3 좋아요한 상품 목록

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

`GET /api/v1/users/{userId}/likes?page&size`로 회원이 자신이 좋아요한 상품을 최신 좋아요 순으로 페이징 조회한다. 진입점은 좋아요(`LikeModel`)이고 결과 항목은 **PRD-1의 `ProductSummary`/`ProductSummaryInfo`를 그대로 재사용**한다. 좋아요 행을 상품·브랜드에 JOIN(둘 다 활성)하고 좋아요 수는 PRD-1과 같은 스칼라 서브쿼리로 집계하며, `myLike.createdAt DESC`로 최신 좋아요 순 정렬한다. 경로 userId가 인증 회원과 다르거나 미존재면 빈 페이지(200)를 반환한다(enumeration 방지). 신규 컨트롤러 `UserLikeV1Controller`를 추가한다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 추가: PRD-1 토대 재사용(`ProductSummary`·`ProductSummaryInfo`·`ProductV1Dto.PageResponse`)

## 컨벤션·결정 점검
- [x] 호출 방향 준수
- [x] 인증: `@LoginUser AuthenticatedUser` — 인증 실패는 ArgumentResolver가 401(UNAUTHENTICATED)
- [x] 본인 일치: `loginUser.userId() != pathUserId` → 빈 페이지(200, 401·403 아님). 인증 자체는 통과한 상태
- [x] page/size 검증: Facade 가드(BrandFacade 선례)
- [x] 결정 1: 좋아요 수 스칼라 서브쿼리 집계
- [x] 결정 7 / LIK-2 대칭: 삭제 상품·브랜드 cascade 삭제 상품은 JOIN(`p.deletedAt IS NULL`·`b.deletedAt IS NULL`)으로 제외
- [x] 정렬: 최신 좋아요 순(`myLike.createdAt DESC`) 고정

## 레이어별 설계 결정 & 파일 맵

### interfaces (신규)
- `interfaces/api/like/UserLikeV1Controller.java`(신규) — `@RequestMapping("/api/v1/users/{userId}/likes")`. `@GetMapping readLikedProducts(@PathVariable Long userId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size, @LoginUser AuthenticatedUser loginUser)` → `likeFacade.readLikedProducts(loginUser.userId(), userId, page, size)` → `ApiResponse.success(ProductV1Dto.PageResponse.from(page))`(PRD-1 응답 재사용).
- `interfaces/api/like/UserLikeV1ApiSpec.java`(신규) — `@Tag` + `readLikedProducts` 선언(`@Parameter(hidden=true)` loginUser).

### application (편집)
- `application/like/LikeFacade.java`(편집) — `readLikedProducts(Long authUserId, Long pathUserId, int page, int size)` `@Transactional(readOnly=true)`: page/size 가드 → `authUserId`와 `pathUserId` 불일치면 `Page.empty(PageRequest.of(page, size))` 반환 → 일치하면 `likeRepository.findLikedProductSummaries(authUserId, page, size).map(ProductSummaryInfo::from)`. 반환 `Page<ProductSummaryInfo>`.

### domain (편집)
- `domain/like/LikeRepository.java`(편집) — `Page<ProductSummary> findLikedProductSummaries(Long userId, int page, int size)` 추가(`ProductSummary`는 `domain.product` 재사용).

### infrastructure (편집)
- `infrastructure/like/LikeJpaRepository.java`(편집) — `findLikedProductSummaries(@Param("userId") Long userId, Pageable pageable)`: `SELECT new com.loopers.domain.product.ProductSummary(p.id, p.name.value, b.id, b.name.value, p.price.value, p.stock.value, (SELECT COUNT(l.id) FROM LikeModel l WHERE l.productId = p.id)) FROM LikeModel myLike JOIN ProductModel p ON p.id = myLike.productId AND p.deletedAt IS NULL JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL WHERE myLike.userId = :userId ORDER BY myLike.createdAt DESC` + `countQuery`(동일 JOIN/WHERE `COUNT(myLike.id)`).
- `infrastructure/like/LikeRepositoryImpl.java`(편집) — `findLikedProductSummaries`: `PageRequest.of(page, size)`(정렬 쿼리 고정) 위임.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 신규 `UserLikeV1Controller`(`/api/v1/users/{userId}/likes`) | 기존 `LikeV1Controller`는 `/api/v1/products/{productId}/likes`로 base path가 달라 한 컨트롤러로 못 묶음 | LikeV1Controller에 메서드 추가 — class-level path 충돌(기각) |
| 쿼리 진입점 `LikeModel`(LikeRepository에 배치) | "회원의 좋아요"가 진입 기준, 결과만 상품 projection | ProductRepository에 배치 — 좋아요 진입 의미가 흐려짐(기각) |
| userId 불일치 시 빈 페이지 | 비기능 요구(enumeration 방지) — 타인 식별자 존재·활동 비노출 | 404/403 — 식별자 존재 여부 노출(기각) |
| PRD-1 `ProductSummary`/`ProductSummaryInfo`/`PageResponse` 재사용 | 항목 필드 집합이 PRD-1과 동일 | 별도 like projection — 중복(기각) |
