# Task: PRD-2 상품 상세 (public, 좋아요 수)

**Plan**: ./plan.md

> 완료 시 `- [ ]` → `- [X]`. 경로 prefix `apps/commerce-api/src/{main,test}/java/com/loopers/`. PRD-1 토대 재사용.

## Phase 1: 단건 상세 조회 (`GET /api/v1/products/{productId}`)

- [X] T001 `ProductDetail` read-model record — `domain/product/ProductDetail.java`(productId·name·description·brandId·brandName·price·stock·likeCount)
- [X] T002 `ProductRepository.getActiveDetailById(Long id): ProductDetail` 선언 — `domain/product/ProductRepository.java`
- [X] T003 `ProductJpaRepository.findActiveDetailById`(JPQL 단건 + 좋아요 서브쿼리 + 브랜드 JOIN ON, `Optional<ProductDetail>`) — `infrastructure/product/ProductJpaRepository.java`
- [X] T004 `ProductRepositoryImpl.getActiveDetailById`(orElseThrow NOT_FOUND) — `infrastructure/product/ProductRepositoryImpl.java`
- [X] T005 통합 테스트 — `test/.../infrastructure/product/ProductRepositoryIntegrationTest.java`(활성+브랜드명 조인 / 좋아요 수 집계(0건 포함) / 삭제·미존재 → 부재)
- [X] T006 `ProductDetailInfo` + `from(ProductDetail)`(available=stock>0) — `application/product/ProductDetailInfo.java`
- [X] T007 `ProductFacade.readProduct(Long productId)` `@Transactional(readOnly=true)` + 단위 테스트 — `application/product/ProductFacade.java`, `test/.../application/product/ProductFacadeTest.java`(ReadProduct: 활성 반환·삭제/미존재 NOT_FOUND)
- [X] T008 `ProductV1Dto.DetailResponse` + `ProductV1ApiSpec.readProduct` + `ProductV1Controller` GET `/{productId}` — `interfaces/api/product/`
- [X] T009 E2E — `test/.../interfaces/api/ProductV1ApiE2ETest.java`(ReadProduct: 200 응답 키(설명 포함·재고 가용 여부·좋아요 수) / 관리자 전용 필드 미포함 / 미존재 404 / 삭제 상품 404)

## Phase 2: 마무리
- [X] T050 spec 테스트 계획 대비 누락 점검
- [X] T051 `.http` — `http/commerce-api/product-v1.http`(상세 추가)
