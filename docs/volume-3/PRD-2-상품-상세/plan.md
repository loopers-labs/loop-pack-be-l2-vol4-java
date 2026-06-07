# Plan: PRD-2 상품 상세 (public, 좋아요 수)

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

`GET /api/v1/products/{productId}`로 누구나 단건 상세를 조회한다. PRD-1과 같은 메커니즘(브랜드명 ad-hoc JOIN + 좋아요 수 스칼라 서브쿼리)을 단건에 적용하되 **설명을 포함**한다. read-model은 별도 record `ProductDetail`(목록 `ProductSummary` + description). 활성 상품이 아니면 404. PRD-1이 세운 public 컨트롤러(`ProductV1Controller`)에 메서드만 추가한다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 추가: 없음(PRD-1 토대 재사용)

## 컨벤션·결정 점검
- [x] 호출 방향 준수, public(인증 없음)
- [x] 결정 1: 좋아요 수 스칼라 서브쿼리 집계
- [x] 결정 7: `p.deletedAt IS NULL` + 브랜드 `b.deletedAt IS NULL` JOIN 필터. 미존재·삭제 → 404
- [x] 재고 가용 여부: `available = stock > 0` Info 매핑 파생
- [x] NOT_FOUND 매핑: 기존 `getActiveById` 선례대로 **repository 조회에서 throw**(`getActiveDetailById`)

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `interfaces/api/product/ProductV1Controller.java`(편집) — `@GetMapping("/{productId}") readProduct(@PathVariable Long productId)` → `productFacade.readProduct(productId)` → `ApiResponse.success(ProductV1Dto.DetailResponse.from(info))`.
- `interfaces/api/product/ProductV1Dto.java`(편집) — `DetailResponse(Long productId, String name, String description, BrandResponse brand, int price, boolean available, long likeCount)` + `from(ProductDetailInfo)`. `BrandResponse`는 PRD-1 것 재사용.
- `interfaces/api/product/ProductV1ApiSpec.java`(편집) — `readProduct` 선언.

### application (신규/편집)
- `application/product/ProductDetailInfo.java`(신규) — record(productId·name·description·brandId·brandName·price·available·likeCount). `from(ProductDetail)`: available=stock>0.
- `application/product/ProductFacade.java`(편집) — `readProduct(Long productId)` 신규 `@Transactional(readOnly = true)`: `productRepository.getActiveDetailById(productId)` → `ProductDetailInfo.from`.

### domain (신규/편집)
- `domain/product/ProductDetail.java`(신규) — read-model record(productId·name·description·brandId·brandName·price·stock·likeCount).
- `domain/product/ProductRepository.java`(편집) — `ProductDetail getActiveDetailById(Long id)` 추가.

### infrastructure (편집)
- `infrastructure/product/ProductJpaRepository.java`(편집) — `Optional<ProductDetail> findActiveDetailById(@Param("productId") Long productId)`: `SELECT new com.loopers.domain.product.ProductDetail(p.id, p.name.value, p.description, b.id, b.name.value, p.price.value, p.stock.value, (SELECT COUNT(l.id) FROM LikeModel l WHERE l.productId = p.id)) FROM ProductModel p JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL WHERE p.id = :productId AND p.deletedAt IS NULL`. GROUP BY 없음(서브쿼리 집계).
- `infrastructure/product/ProductRepositoryImpl.java`(편집) — `getActiveDetailById`: `findActiveDetailById(id).orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."))`.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `ProductDetail` 별도 projection(설명 포함) | 목록은 TEXT(description) 불필요. 단건만 설명 노출이라 contract 분리 | 단일 projection 공유 — 목록 쿼리가 TEXT까지 가져옴(기각) |
| 좋아요 수 스칼라 서브쿼리 | PRD-1과 동일. 단건도 GROUP BY 없이 집계 | LEFT JOIN+GROUP BY — description(TEXT) GROUP BY 불가(기각) |
