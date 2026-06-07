# Task: BRD-3 브랜드 상세 (admin)

**Plan**: ./plan.md

> 완료 시 `- [ ]` → `- [X]`. 경로 prefix `apps/commerce-api/src/{main,test}/java/com/loopers/`.
> 신규 도메인/응용 로직 없음 — `BrandFacade.readBrand`(BRD-1)·`getActiveById`·`BrandInfo` 재사용, admin 표현 계층만 추가.

## Phase 1: 상세 유스케이스 (`GET /api-admin/v1/brands/{brandId}`)

- [X] T010 `BrandAdminV1Dto.DetailResponse`(brandId·name·description·createdAt·updatedAt, from `BrandInfo`) — BRD-2 목록 항목도 이 record 재사용 — `interfaces/api/brand/BrandAdminV1Dto.java`
- [X] T011 `BrandAdminV1ApiSpec.readBrand`(상세) + `BrandAdminV1Controller` GET `/{brandId}`(`brandFacade.readBrand` 재사용) — `interfaces/api/brand/`
- [X] T012 E2E — `test/.../interfaces/api/BrandAdminV1ApiE2ETest.java`(ReadBrandDetail: 200 시각 포함 / 403 / 404)

## Phase 2: 마무리
- [X] T050 spec 테스트 계획 대비 누락 점검
- [X] T051 `.http` — `http/commerce-api/brand-admin-v1.http`(상세 추가)
