# Plan: PRD-1 상품 목록 (public, 좋아요 수)

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

`GET /api/v1/products?brandId&sort&page&size`로 누구나 상품을 페이징 조회한다. 상품에 **브랜드명을 ad-hoc 엔티티 JOIN**으로 붙이고(Product는 `brandId`를 스칼라로 보유 — 매핑 연관 없음), **좋아요 수는 스칼라 상관 서브쿼리**로 매 조회 집계한다(결정 1). GROUP BY 없이 서브쿼리로 집계해 정렬·페이징·count 쿼리를 단순하게 유지한다. 읽기 결과는 도메인 read-model record(`ProductSummary`)로 받고, 페이지네이션은 BRD-2 패턴(Spring Data `Pageable`/`Page` + 자체 `PageResponse` DTO, page/size는 Facade 가드)을 따른다. **이 시나리오가 Product read 토대(`ProductSummary` projection·`ProductSortType`·public 컨트롤러)를 처음 세운다 — PRD-2·LIK-3가 재사용**.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA(Hibernate 6) / MySQL / commerce-api
- 추가: Spring Data 페이징(`Pageable`/`Page`/`PageRequest`/`Sort`), JPQL `@Query`(생성자 표현식 projection + 엔티티 `JOIN ... ON` + 스칼라 서브쿼리)

## 컨벤션·결정 점검
- [x] 호출 방향 interfaces → application → domain → infrastructure 준수
- [x] 인증 불필요(public) — `@LoginUser` 미사용
- [x] 검증 단일화: sort 문자열 → `ProductSortType.from(String)` VO식 검증(허용 외 → `CoreException(BAD_REQUEST)`). page/size는 `BrandFacade` 선례대로 **Facade 가드**(`@Validated` 미사용 — ApiSpec 인터페이스+JDK프록시 바인딩 깨짐)
- [x] 결정 1(좋아요 매 조회 집계): 스칼라 상관 서브쿼리 `(SELECT COUNT(l.id) FROM LikeModel l WHERE l.productId = p.id)`
- [x] 결정 7(soft delete): `p.deletedAt IS NULL`. 브랜드 삭제 상품 제외는 cascade(상품 deletedAt)로 1차 보장 + **JOIN에 `b.deletedAt IS NULL` 방어 필터** 동시 적용
- [x] 재고 가용 여부: projection은 `stock`(int) 보유, `available = stock > 0`은 Info 매핑에서 파생(스냅샷 컬럼 추가 안 함)
- [x] 응답: `Page` 직접 직렬화 금지 → `ProductV1Dto.PageResponse`로 매핑(BRD-2와 동일)

## 레이어별 설계 결정 & 파일 맵

### interfaces (신규)
- `interfaces/api/product/ProductV1Controller.java` — `@RequestMapping("/api/v1/products")`. `@GetMapping readProducts(@RequestParam(required=false) Long brandId, @RequestParam(defaultValue="latest") String sort, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size)` → `productFacade.readProducts(brandId, sort, page, size)` → `ApiResponse.success(ProductV1Dto.PageResponse.from(page))`.
- `interfaces/api/product/ProductV1Dto.java` — `BrandResponse(Long brandId, String name)`, `SummaryResponse(Long productId, String name, BrandResponse brand, int price, boolean available, long likeCount)`(`from(ProductSummaryInfo)`), `PageResponse(List<SummaryResponse> content, int page, int size, long totalElements, int totalPages)`(`from(Page<ProductSummaryInfo>)`).
- `interfaces/api/product/ProductV1ApiSpec.java` — `@Tag` + `readProducts` 선언.

### application (신규)
- `application/product/ProductSummaryInfo.java` — record(`Long productId, String name, Long brandId, String brandName, int price, boolean available, long likeCount`). `from(ProductSummary)`: `available = summary.stock() > 0`.
- `application/product/ProductFacade.java`(편집) — `readProducts(Long brandId, String sort, int page, int size)` 신규 `@Transactional(readOnly = true)`: page/size 가드(page<0·size 1~100 밖 → `CoreException(BAD_REQUEST)`, 상수 `MIN_PAGE_SIZE`/`MAX_PAGE_SIZE`) → `ProductSortType.from(sort)` → `productRepository.findActiveSummaries(brandId, sortType, page, size)` → `.map(ProductSummaryInfo::from)`.

### domain (신규/편집)
- `domain/product/ProductSummary.java`(신규) — read-model record(`Long productId, String name, Long brandId, String brandName, Integer price, Integer stock, Long likeCount`). JPA 무관 순수 데이터.
- `domain/product/ProductSortType.java`(신규) — enum `LATEST`(createdAt desc)·`PRICE_ASC`(price asc)·`LIKES_DESC`(좋아요 수 desc). `from(String)`: 허용 외/`null` → `CoreException(BAD_REQUEST)`. `latest`/`price_asc`/`likes_desc` 매핑.
- `domain/product/ProductRepository.java`(편집) — `Page<ProductSummary> findActiveSummaries(Long brandId, ProductSortType sort, int page, int size)` 추가(`Pageable` 미노출).

### infrastructure (편집)
- `infrastructure/product/ProductJpaRepository.java`(편집) — 두 JPQL 메서드:
  - `findActiveSummaries(@Param("brandId") Long brandId, Pageable pageable)` — `SELECT new com.loopers.domain.product.ProductSummary(p.id, p.name.value, b.id, b.name.value, p.price.value, p.stock.value, (SELECT COUNT(l.id) FROM LikeModel l WHERE l.productId = p.id)) FROM ProductModel p JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL WHERE p.deletedAt IS NULL AND (:brandId IS NULL OR p.brandId = :brandId)` + `countQuery`(동일 WHERE에 `COUNT(p.id)`). 정렬은 Pageable `Sort`로(LATEST·PRICE_ASC).
  - `findActiveSummariesOrderByLikeCount(@Param("brandId") Long brandId, Pageable pageable)` — 같은 본문 + `ORDER BY (SELECT COUNT(l.id) FROM LikeModel l WHERE l.productId = p.id) DESC, p.id DESC`(좋아요 수 정렬은 Sort로 표현 불가 → 쿼리에 고정). Pageable은 unsorted.
- `infrastructure/product/ProductRepositoryImpl.java`(편집) — `findActiveSummaries`: `switch(sort)` — `LATEST` → `PageRequest.of(page, size, Sort.by(DESC, "createdAt"))` + `findActiveSummaries`; `PRICE_ASC` → `Sort.by(ASC, "price.value")` + `findActiveSummaries`; `LIKES_DESC` → `PageRequest.of(page, size)` + `findActiveSummariesOrderByLikeCount`.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 좋아요 수 = 스칼라 상관 서브쿼리(GROUP BY 미사용) | LEFT JOIN+GROUP BY는 TEXT/임베디드 다수를 GROUP BY에 넣어야 하고 count 쿼리가 복잡. 서브쿼리는 정렬·페이징·count 모두 단순 | LEFT JOIN + GROUP BY 집계 — GROUP BY 부담·count 쿼리 복잡(기각) |
| 좋아요 정렬만 전용 메서드, 최신·가격은 Pageable `Sort` | `Sort`로 집계 서브쿼리 정렬을 표현 불가. 2개 메서드로 분기 | QueryDSL 동적 정렬 — 프로젝트 선례 없음·새 인프라(기각). likeCount 컬럼화 — 결정 1 B안(이번 라운드 기각) |
| 브랜드 `JOIN ... ON`(ad-hoc 엔티티 조인) | Product가 brandId를 스칼라로 보유(@ManyToOne 없음). Hibernate 6 root 엔티티 `JOIN ON` 지원 | `@ManyToOne` 매핑 추가 — 기존 엔티티 구조 변경·범위 밖(기각) |
| read-model `ProductSummary`(도메인 record) | 좋아요 수·브랜드명은 ProductModel에 없음. 정렬을 위해 집계가 쿼리에 있어야 해 엔티티 반환 불가 | `Page<ProductModel>` 반환 후 Facade 조립 — 좋아요 정렬 불가(기각) |
