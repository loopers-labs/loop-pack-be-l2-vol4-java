# Plan: PRD-6 상품 수정

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

`PUT /api-admin/v1/products/{productId}`로 관리자가 상품을 수정한다. `ProductModel.update(name, description, price, stock)` 행위를 추가하고(`Name`·`Price`·`Stock` VO 재검증), `ProductFacade.updateProduct`가 대상 활성 조회(`ProductRepository.getActiveById`, 없거나 삭제 시 NOT_FOUND)로 존재를 보장한 뒤 갱신한다. 소속 브랜드는 수정 대상이 아니다(요청에 brandId 없음). admin 인증은 BRD-4 `AdminAuthInterceptor`가 `/api-admin/**`를 가드하므로 추가 토대 없음. BRD-5 브랜드 수정을 그대로 본뜬다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (기존 의존성으로 충족)

## 컨벤션·결정 점검
- [x] 호출 방향 interfaces → application → domain → infrastructure 준수 (Facade가 Repository 주입, 도메인 서비스 없음 — Brand/Product 패턴)
- [x] 검증 단일화: 이름 길이(1~100)는 `Name.from()`, 가격·재고 `≥0`은 `Price`/`Stock.from()`가 단일 원천. DTO는 null 방어(`@NotBlank`/`@NotNull`) + 수치 1차 방어(`@PositiveOrZero`) — PRD-5 선례와 정합
- [x] CRUD 네이밍: Facade·Controller 메서드 `updateProduct` (create*/update* 컨벤션)
- [x] admin 인증: `/api-admin/**` 인터셉터가 가드(컨트롤러 인증 파라미터 없음)
- [x] find/get 컨벤션: 존재 보장 조회는 `getActiveById`(없으면 NOT_FOUND) — BRD-5 `getActiveById` 선례
- [x] 결정 7(soft delete): 대상 조회는 `deletedAt IS NULL` 행만(삭제된 상품은 404)
- [x] 브랜드 변경 불가: 수정 DTO에 brandId 미수신(소스 규칙)
- [x] PUT 전체 치환: 설명 미입력은 null 치환(BRD-5 선례, PATCH 아님)

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `interfaces/api/product/ProductAdminV1Controller.java` (편집) — `@PutMapping("/{productId}")` `updateProduct(@PathVariable Long productId, @Valid @RequestBody ProductAdminV1Dto.UpdateRequest request)` 추가. 200 OK(`@ResponseStatus` 미지정 — 기본 200, BRD-5 선례). `productFacade.updateProduct(productId, name, description, price, stock)` 후 `ApiResponse.success(UpdateResponse.from(info))`.
- `interfaces/api/product/ProductAdminV1Dto.java` (편집) — 추가:
  - `UpdateRequest(String name @NotBlank, String description, Integer price @NotNull @PositiveOrZero, Integer stock @NotNull @PositiveOrZero)`. **brandId 없음.** message는 CreateRequest와 동일 문구 재사용.
  - `UpdateResponse(Long productId)` + `from(ProductUpdateInfo)`.
- `interfaces/api/product/ProductAdminV1ApiSpec.java` (편집) — `updateProduct(Long productId, UpdateRequest)` `@Operation` 선언 추가.

### application
- `application/product/ProductFacade.java` (편집) — `updateProduct(Long productId, String name, String description, Integer price, Integer stock)` (신규): `ProductModel product = productRepository.getActiveById(productId)` → `product.update(name, description, price, stock)` (managed 엔티티 dirty checking으로 반영) → `ProductUpdateInfo.from(product)`. `@Transactional`(클래스 레벨 이미 존재).
- `application/product/ProductUpdateInfo.java` (신규) — `record ProductUpdateInfo(Long productId)` + `from(ProductModel)`.

### domain (편집)
- `domain/product/ProductModel.java` (편집) — `public void update(String rawName, String rawDescription, Integer rawPrice, Integer rawStock)` 추가: `this.name = Name.from(rawName); this.description = rawDescription; this.price = Price.from(rawPrice); this.stock = Stock.from(rawStock);` [behavioral] (brandId 미변경)
- `domain/product/ProductRepository.java` (편집) — `ProductModel getActiveById(Long id)` 추가(없으면 NOT_FOUND).
- VO: `Name`/`Price`/`Stock` 재사용(신규 없음).

### infrastructure (편집)
- `infrastructure/product/ProductJpaRepository.java` (편집) — `Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id)` 추가.
- `infrastructure/product/ProductRepositoryImpl.java` (편집) — `getActiveById`: `findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new CoreException(NOT_FOUND, "상품이 존재하지 않습니다."))` 위임.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `ProductUpdateInfo`를 `ProductCreateInfo`와 별도로 둠 | 유스케이스별 `*Info` 네이밍 컨벤션. 둘 다 productId만 담지만 의도 구분 (BRD-5 `BrandUpdateInfo` 선례) | 공용 `ProductIdInfo` — 커밋된 `ProductCreateInfo` 개명 유발, 이득 적음 |
