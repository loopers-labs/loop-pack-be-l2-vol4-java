# Task: BRD-1 브랜드 조회 (public 단건)

**Plan**: ./plan.md

> 완료 시 `- [ ]` → `- [X]`. 경로 prefix `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase F: 공용 토대

- [X] T001 `BrandRepository.getActiveById(Long)`(없으면 NOT_FOUND) — BRD-5에서 신설, 공유 (`domain/brand/BrandRepository.java`, `infrastructure/brand/`)
- [X] T002 `BrandInfo`(brandId·name·description·createdAt·updatedAt + `from(BrandModel)`) — 단건·목록·상세 공유 — `application/brand/BrandInfo.java`

## Phase 1: 조회 유스케이스 (`GET /api/v1/brands/{brandId}`, 무인증)

- [X] T010 `BrandFacade.readBrand(brandId)`(@Transactional(readOnly), getActiveById → BrandInfo) + 단위 테스트 — `application/brand/BrandFacade.java`, `test/.../application/brand/BrandFacadeTest.java`(ReadBrand: 활성 반환 / 부재·삭제 NOT_FOUND)
- [X] T011 public 표현 계층 신규 — `interfaces/api/brand/BrandV1Controller.java`(`/api/v1/brands` GET `/{brandId}`, 무인증), `BrandV1Dto.ReadResponse`(brandId·name·description, 시각 미노출), `BrandV1ApiSpec.java`
- [X] T012 E2E 신규 — `test/.../interfaces/api/BrandV1ApiE2ETest.java`(200 + 키 id·name·description / 404)

## Phase 2: 마무리
- [X] T050 spec 테스트 계획 대비 누락 점검
- [X] T051 `.http` — `http/commerce-api/brand-v1.http`(public 조회 신규)
