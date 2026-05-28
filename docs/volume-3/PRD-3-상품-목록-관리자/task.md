# Task: PRD-3 상품 목록 (admin)

**Plan**: ./plan.md

> 완료 시 `- [ ]` → `- [X]`. 경로 prefix `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase 1: 관리자 목록 조회 (`GET /api-admin/v1/products`)

- [X] T001 `ProductAdminView` read-model record — `domain/product/ProductAdminView.java`(productId·name·description·brandId·brandName·price·stock·createdAt·updatedAt)
- [X] T002 `ProductRepository.findActiveAdminViews(Long brandId, int page, int size): Page<ProductAdminView>` 선언 — `domain/product/ProductRepository.java`
- [X] T003 `ProductJpaRepository.findActiveAdminViews`(JPQL projection + 브랜드 JOIN ON + `ORDER BY p.createdAt DESC` + countQuery) — `infrastructure/product/ProductJpaRepository.java`
- [X] T004 `ProductRepositoryImpl.findActiveAdminViews`(PageRequest.of 위임) — `infrastructure/product/ProductRepositoryImpl.java`
- [X] T005 통합 테스트 — `test/.../infrastructure/product/ProductRepositoryIntegrationTest.java`(활성만 / brandId 필터 / 등록 시각 내림차순 / 페이징·총 개수 / 브랜드명 조인 / 정확 재고)
- [X] T006 `ProductAdminInfo` + `from(ProductAdminView)` — `application/product/ProductAdminInfo.java`
- [X] T007 `ProductFacade.readProductsForAdmin(brandId, page, size)` 조회+map, `@Transactional(readOnly=true)` + 단위 테스트 — `application/product/ProductFacade.java`, `test/.../application/product/ProductFacadeTest.java`(ReadProductsForAdmin: 필터)
- [X] T008 `ProductAdminV1Dto`(BrandResponse·DetailResponse·PageResponse) + `ProductAdminV1ApiSpec.readProducts` + `ProductAdminV1Controller` GET — `interfaces/api/product/`
- [X] T009 E2E — `test/.../interfaces/api/ProductAdminV1ApiE2ETest.java`(ReadProducts: 200 메타·항목 키(설명·정확 재고·등록/갱신 시각) / brandId 필터 / 빈 결과 200 / 403)

## Phase 2: 마무리
- [X] T050 spec 테스트 계획 대비 누락 점검
- [X] T051 `.http` — `http/commerce-api/product-admin-v1.http`(목록 추가)
