# Plan: PRD-3 상품 목록 (admin)

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

`GET /api-admin/v1/products?brandId&page&size`로 관리자가 상품을 페이징 조회한다. 좋아요 집계가 없어 GROUP BY·서브쿼리가 불필요하고, 브랜드명 ad-hoc JOIN + 재고 **정확 수량** + 등록/갱신 시각을 담은 read-model `ProductAdminView`를 반환한다. 정렬은 등록 시각 내림차순 고정(쿼리 ORDER BY). 인증은 기존 `/api-admin/**` 인터셉터(403), 페이지네이션은 BRD-2 패턴. 기존 `ProductAdminV1Controller`(등록/수정/삭제)에 GET 추가.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 추가: Spring Data 페이징, JPQL `@Query` projection(JOIN ON, 집계 없음)

## 컨벤션·결정 점검
- [x] 호출 방향 준수
- [x] admin 인증: `/api-admin/**` 인터셉터(`AdminAuthInterceptor`) 403 — 컨트롤러 추가 코드 없음
- [x] page/size: 검증 없이 클라이언트 신뢰
- [x] 결정 7: `p.deletedAt IS NULL` + 브랜드 `b.deletedAt IS NULL` JOIN 필터
- [x] 재고 정확 수량 노출(대고객 가용 여부와 대비) — `ProductAdminView`는 `stock` 정수 그대로
- [x] 응답: `Page` 직렬화 금지 → `ProductAdminV1Dto.PageResponse`

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `interfaces/api/product/ProductAdminV1Controller.java`(편집) — `@GetMapping readProducts(@RequestParam(required=false) Long brandId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size)` → `productFacade.readProductsForAdmin(brandId, page, size)` → `ApiResponse.success(ProductAdminV1Dto.PageResponse.from(page))`.
- `interfaces/api/product/ProductAdminV1Dto.java`(편집) — `BrandResponse(Long brandId, String name)`, `DetailResponse(Long productId, String name, String description, BrandResponse brand, int price, int stock, ZonedDateTime createdAt, ZonedDateTime updatedAt)`(`from(ProductAdminInfo)`), `PageResponse(List<DetailResponse> content, int page, int size, long totalElements, int totalPages)`(`from(Page<ProductAdminInfo>)`).
- `interfaces/api/product/ProductAdminV1ApiSpec.java`(편집) — `readProducts` 선언.

### application (신규/편집)
- `application/product/ProductAdminInfo.java`(신규) — record(productId·name·description·brandId·brandName·price·stock·createdAt·updatedAt). `from(ProductAdminView)`.
- `application/product/ProductFacade.java`(편집) — `readProductsForAdmin(Long brandId, int page, int size)` `@Transactional(readOnly=true)`: `productRepository.findActiveAdminViews(brandId, page, size)` → `.map(ProductAdminInfo::from)`.

### domain (신규/편집)
- `domain/product/ProductAdminView.java`(신규) — read-model record(productId·name·description·brandId·brandName·price·stock·createdAt·updatedAt).
- `domain/product/ProductRepository.java`(편집) — `Page<ProductAdminView> findActiveAdminViews(Long brandId, int page, int size)` 추가.

### infrastructure (편집)
- `infrastructure/product/ProductJpaRepository.java`(편집) — `findActiveAdminViews(@Param("brandId") Long brandId, Pageable pageable)`: `SELECT new com.loopers.domain.product.ProductAdminView(p.id, p.name.value, p.description, b.id, b.name.value, p.price.value, p.stock.value, p.createdAt, p.updatedAt) FROM ProductModel p JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL WHERE p.deletedAt IS NULL AND (:brandId IS NULL OR p.brandId = :brandId) ORDER BY p.createdAt DESC` + `countQuery`(동일 WHERE `COUNT(p.id)`).
- `infrastructure/product/ProductRepositoryImpl.java`(편집) — `findActiveAdminViews`: `PageRequest.of(page, size)`(정렬 쿼리 고정) 위임.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 정렬을 쿼리 `ORDER BY p.createdAt DESC` 고정 | 정렬 옵션 1종뿐(등록 시각 내림차순) | Pageable Sort 주입 — 옵션 1종에 불필요한 유연성(기각) |
| public과 별도 `ProductAdminView`/`ProductAdminInfo` | admin은 좋아요 수·가용 여부 대신 정확 재고·시각 — 응답 contract 자체가 다름 | public projection 재사용 — 노출 필드 충돌(기각) |
