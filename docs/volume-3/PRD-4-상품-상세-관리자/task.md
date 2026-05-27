# Task: PRD-4 상품 상세 (admin)

**Plan**: ./plan.md

> 완료 시 `- [ ]` → `- [X]`. 경로 prefix `apps/commerce-api/src/{main,test}/java/com/loopers/`. PRD-3 토대 재사용.

## Phase 1: 관리자 단건 상세 (`GET /api-admin/v1/products/{productId}`)

- [X] T001 `ProductRepository.getActiveAdminViewById(Long id): ProductAdminView` 선언 — `domain/product/ProductRepository.java`
- [X] T002 `ProductJpaRepository.findActiveAdminViewById`(JPQL 단건 + 브랜드 JOIN ON, `Optional<ProductAdminView>`) — `infrastructure/product/ProductJpaRepository.java`
- [X] T003 `ProductRepositoryImpl.getActiveAdminViewById`(orElseThrow NOT_FOUND) — `infrastructure/product/ProductRepositoryImpl.java`
- [X] T004 통합 테스트 — `test/.../infrastructure/product/ProductRepositoryIntegrationTest.java`(활성+브랜드명 조인·정확 재고 / 삭제·미존재 → 부재)
- [X] T005 `ProductFacade.readProductForAdmin(Long productId)` `@Transactional(readOnly=true)` + 단위 테스트 — `application/product/ProductFacade.java`, `test/.../application/product/ProductFacadeTest.java`(ReadProductForAdmin: 활성 반환·삭제/미존재 NOT_FOUND)
- [X] T006 `ProductAdminV1ApiSpec.readProduct` + `ProductAdminV1Controller` GET `/{productId}`(DetailResponse 재사용) — `interfaces/api/product/`
- [X] T007 E2E — `test/.../interfaces/api/ProductAdminV1ApiE2ETest.java`(ReadProduct: 200 응답 키(설명·정확 재고·등록/갱신 시각) / 403 / 미존재 404 / 삭제 상품 404)

## Phase 2: 마무리
- [X] T050 spec 테스트 계획 대비 누락 점검
- [X] T051 `.http` — `http/commerce-api/product-admin-v1.http`(상세 추가)
