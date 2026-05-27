# Plan: PRD-4 상품 상세 (admin)

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

`GET /api-admin/v1/products/{productId}`로 관리자가 단건 상세를 조회한다. PRD-3의 `ProductAdminView`(정확 재고·시각 포함)를 단건으로 재사용한다. 활성 상품이 아니면 404, 인증 실패는 인터셉터 403. 기존 `ProductAdminV1Controller`에 GET 메서드만 추가.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 추가: 없음(PRD-3 토대 재사용)

## 컨벤션·결정 점검
- [x] 호출 방향 준수
- [x] admin 인증: 인터셉터 403
- [x] 결정 7: `p.deletedAt IS NULL` + 브랜드 `b.deletedAt IS NULL`. 미존재·삭제 → 404
- [x] 재고 정확 수량 노출
- [x] NOT_FOUND 매핑: repository 조회에서 throw(`getActiveAdminViewById`)

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `interfaces/api/product/ProductAdminV1Controller.java`(편집) — `@GetMapping("/{productId}") readProduct(@PathVariable Long productId)` → `productFacade.readProductForAdmin(productId)` → `ApiResponse.success(ProductAdminV1Dto.DetailResponse.from(info))`.
- `interfaces/api/product/ProductAdminV1Dto.java`(편집) — `DetailResponse` 재사용(PRD-3). 추가 타입 없음.
- `interfaces/api/product/ProductAdminV1ApiSpec.java`(편집) — `readProduct` 선언.

### application (편집)
- `application/product/ProductFacade.java`(편집) — `readProductForAdmin(Long productId)` `@Transactional(readOnly=true)`: `productRepository.getActiveAdminViewById(productId)` → `ProductAdminInfo.from`.

### domain (편집)
- `domain/product/ProductRepository.java`(편집) — `ProductAdminView getActiveAdminViewById(Long id)` 추가.

### infrastructure (편집)
- `infrastructure/product/ProductJpaRepository.java`(편집) — `Optional<ProductAdminView> findActiveAdminViewById(@Param("productId") Long productId)`: `SELECT new com.loopers.domain.product.ProductAdminView(p.id, p.name.value, p.description, b.id, b.name.value, p.price.value, p.stock.value, p.createdAt, p.updatedAt) FROM ProductModel p JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL WHERE p.id = :productId AND p.deletedAt IS NULL`.
- `infrastructure/product/ProductRepositoryImpl.java`(편집) — `getActiveAdminViewById`: `findActiveAdminViewById(id).orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."))`.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `ProductAdminView` 단건 재사용 | PRD-3 목록 항목과 동일 필드 집합 | 별도 admin 상세 projection — 중복(기각) |
