# Task: BRD-5 브랜드 수정

**Plan**: ./plan.md

> 완료 시 `- [ ]` → `- [X]`. 경로 prefix `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase F: 공용 토대 (Brand aggregate 확장 — BRD-1·3과 공유)

- [X] T001 `BrandModel.update(name, description)` 추가 + 단위 테스트 — `domain/brand/BrandModel.java`, `test/.../domain/brand/BrandModelTest.java`(Update nested: 갱신 / 이름 위반 BAD_REQUEST)
- [X] T002 `BrandRepository.getActiveById(Long)`(없으면 NOT_FOUND, find/get 컨벤션) + `existsActiveByNameAndIdNot(String, Long)` — `domain/brand/BrandRepository.java`
- [X] T003 `BrandJpaRepository.findByIdAndDeletedAtIsNull` + `existsByNameValueAndDeletedAtIsNullAndIdNot`, `BrandRepositoryImpl.getActiveById`(orElseThrow NOT_FOUND)·`existsActiveByNameAndIdNot` 위임 — `infrastructure/brand/`
- [X] T004 통합 테스트 — `test/.../infrastructure/brand/BrandRepositoryIntegrationTest.java`(GetActiveById: 활성 반환·삭제/부재 NOT_FOUND / ExistsActiveByNameAndIdNot: 자신·삭제 제외)

## Phase 1: 수정 유스케이스 (`PUT /api-admin/v1/brands/{brandId}`)

- [X] T010 `BrandFacade.updateBrand`(getActiveById → existsActiveByNameAndIdNot CONFLICT → update) + `BrandUpdateInfo` + 단위 테스트 — `application/brand/`, `test/.../application/brand/BrandFacadeTest.java`(UpdateBrand: 부재 NOT_FOUND / 중복 CONFLICT / 정상)
- [X] T011 `BrandAdminV1Dto.UpdateRequest/UpdateResponse` + `BrandAdminV1ApiSpec.updateBrand` + `BrandAdminV1Controller` PUT — `interfaces/api/brand/`
- [X] T012 E2E — `test/.../interfaces/api/BrandAdminV1ApiE2ETest.java`(UpdateBrand: 200 / 403 / 404 / 400 / 409 / 자기 이름 동일 200)

## Phase 2: 마무리
- [X] T050 spec 테스트 계획 대비 누락 점검
- [X] T051 `.http` — `http/commerce-api/brand-admin-v1.http`(수정 추가)
