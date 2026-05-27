# Task: PRD-1 상품 목록 (public, 좋아요 수)

**Plan**: ./plan.md

> 완료 시 `- [ ]` → `- [X]`. 경로 prefix `apps/commerce-api/src/{main,test}/java/com/loopers/`. 구현은 CLAUDE.md·컨벤션 준수.

## Phase 1: Product read 토대 + 목록 조회 (`GET /api/v1/products`)

- [X] T001 `ProductSortType` enum — `domain/product/ProductSortType.java` (LATEST·PRICE_ASC·LIKES_DESC, `from(String)` 허용 외/null → `CoreException(BAD_REQUEST)`) + 단위 테스트 `test/.../domain/product/ProductSortTypeTest.java`(허용값 매핑·무효값 예외 errorType)
- [X] T002 `ProductSummary` read-model record — `domain/product/ProductSummary.java`(productId·name·brandId·brandName·price·stock·likeCount)
- [X] T003 `ProductRepository.findActiveSummaries(Long brandId, ProductSortType sort, int page, int size): Page<ProductSummary>` 선언 — `domain/product/ProductRepository.java`
- [X] T004 `ProductJpaRepository` 두 JPQL 메서드(`findActiveSummaries`(Sort 기반)·`findActiveSummariesOrderByLikeCount`(좋아요 ORDER BY 서브쿼리 고정), 각 `countQuery`) — `infrastructure/product/ProductJpaRepository.java`
- [X] T005 `ProductRepositoryImpl.findActiveSummaries` switch(sort)로 PageRequest+Sort 또는 좋아요 메서드 위임 — `infrastructure/product/ProductRepositoryImpl.java`
- [X] T006 통합 테스트 — `test/.../infrastructure/product/ProductRepositoryIntegrationTest.java`(활성+브랜드활성만 / brandId 필터 / 최신·가격 오름차순·좋아요 많은 순 정렬 / 페이징·총 개수 / 좋아요 수 집계 정확(0건 포함) / 미존재 브랜드 필터→빈)
- [X] T007 `ProductSummaryInfo` + `from(ProductSummary)`(available=stock>0) — `application/product/ProductSummaryInfo.java`
- [X] T008 `ProductFacade.readProducts(brandId, sort, page, size)` 가드+sort 파싱+조회+map, `@Transactional(readOnly=true)` + 단위 테스트 — `application/product/ProductFacade.java`, `test/.../application/product/ProductFacadeTest.java`(ReadProducts: page/size 가드·sort 무효 예외·정렬 위임)
- [X] T009 `ProductV1Dto`(BrandResponse·SummaryResponse·PageResponse) + `ProductV1ApiSpec` + `ProductV1Controller` GET — `interfaces/api/product/`
- [X] T010 E2E — `test/.../interfaces/api/ProductV1ApiE2ETest.java`(ReadProducts: 200 메타·항목 키 / 정렬별 순서 / brandId 필터 / 빈 결과 200 / sort 무효 400 / size·page 위반 400)

## Phase 2: 마무리
- [X] T050 spec 테스트 계획 대비 누락 점검
- [X] T051 `.http` — `http/commerce-api/product-v1.http`(목록)
