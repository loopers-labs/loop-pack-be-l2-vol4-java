# Task: BRD-2 브랜드 목록 (admin)

**Plan**: ./plan.md

> 완료 시 `- [ ]` → `- [X]`. 경로 prefix `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase F: 공용 토대 (페이지네이션)

- [X] T001 `BrandRepository.findActiveByPage(int page, int size)` → `Page<BrandModel>` (Spring Data `Pageable`은 infra 내부에서만) — `domain/brand/BrandRepository.java`
- [X] T002 `BrandJpaRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable)`(정렬을 쿼리 메서드명에 고정 — 매직 문자열·DB정렬 정합) + `BrandRepositoryImpl.findActiveByPage`(`PageRequest.of(page,size)` 위임) — `infrastructure/brand/`
- [X] T003 통합 테스트 — `test/.../infrastructure/brand/BrandRepositoryIntegrationTest.java`(FindActivePage: 삭제행 제외·등록시각 내림차순·총개수)

## Phase 1: 목록 유스케이스 (`GET /api-admin/v1/brands?page&size`)

- [X] T010 `BrandFacade.readBrands(int page, int size)` → `findActiveByPage` → `Page<BrandInfo>` + 단위 테스트 — `application/brand/BrandFacade.java`, `test/.../application/brand/BrandFacadeTest.java`(ReadBrands)
- [X] T011 `BrandAdminV1Dto.PageResponse`(content=`List<DetailResponse>` 재사용, page·size·totalElements·totalPages, from `Page<BrandInfo>`) + `BrandAdminV1ApiSpec.readBrands` + `BrandAdminV1Controller` GET(`@RequestParam(defaultValue)`) — `interfaces/api/brand/`
- [X] T012 E2E — `test/.../interfaces/api/BrandAdminV1ApiE2ETest.java`(ReadBrands: 200 메타·항목 / 빈결과 200 / 403)

## Phase 2: 마무리
- [X] T050 spec 테스트 계획 대비 누락 점검
- [X] T051 `.http` — `http/commerce-api/brand-admin-v1.http`(목록 추가)
