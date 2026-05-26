# Plan: BRD-5 브랜드 수정

**Spec**: ./spec.md
**작성일**: 2026-05-25

## 요약

`PUT /api-admin/v1/brands/{brandId}`로 관리자가 브랜드를 수정한다. `BrandModel.update(name, description)` 행위를 추가하고, `BrandFacade.updateBrand`가 대상 활성 조회(존재 보장)·자신 제외 이름 중복 검사 후 갱신한다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 추가 의존성: 없음

## 컨벤션·결정 점검
- [x] 호출 방향 interfaces → application → domain → infrastructure
- [x] CRUD 네이밍: Facade·Controller 메서드 `updateBrand`
- [x] 검증 단일화: 이름은 `BrandName.from()`, DTO는 `@NotBlank`로 null/blank 1차 방어
- [x] admin 인증: `/api-admin/**` 인터셉터가 가드(컨트롤러 인증 파라미터 없음)
- [x] 결정 7(soft delete): 대상·중복 검사 모두 `deletedAt IS NULL` 행만
- [x] 설명 String 직접 보유(BRD-4 결정 계승), null 허용 교체

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `BrandAdminV1Controller`에 `@PutMapping("/{brandId}")` `updateBrand(@PathVariable Long brandId, @Valid @RequestBody UpdateRequest)` 추가. 200 OK. `brandFacade.updateBrand(brandId, name, description)` 후 `UpdateResponse.from(info)`.
- `BrandAdminV1Dto`에 `UpdateRequest(String name @NotBlank, String description)`, `UpdateResponse(Long brandId) + from(BrandUpdateInfo)` 추가.
- `BrandAdminV1ApiSpec`에 `updateBrand` 선언 추가.

### application
- `BrandFacade.updateBrand(Long brandId, String name, String description)` (신규): `brandRepository.getActiveById(brandId)` → `existsActiveByNameAndIdNot(name, brandId)`면 `CoreException(CONFLICT)` → `brand.update(name, description)` (managed 엔티티 dirty checking으로 반영) → `BrandUpdateInfo.from(brand)`.
- 존재 보장 조회는 `BrandRepository.getActiveById`(없으면 NOT_FOUND)가 담당 — Facade에 별도 `mustFind*` 헬퍼 없음 (find/get 컨벤션, BRD-1/3 공유).
- `application/brand/BrandUpdateInfo.java` (신규) — `record(Long brandId)` + `from(BrandModel)`.

### domain (편집)
- `BrandModel.update(name, description)` (신규 메서드) — `this.name = BrandName.from(name); this.description = description;` [behavioral]
- `BrandRepository` (편집): `BrandModel getActiveById(Long id)`(없으면 NOT_FOUND, BRD-1/3 공유), `boolean existsActiveByNameAndIdNot(String name, Long id)` 추가.

### infrastructure (편집)
- `BrandJpaRepository`: `Optional<BrandModel> findByIdAndDeletedAtIsNull(Long id)`, `boolean existsByNameValueAndDeletedAtIsNullAndIdNot(String value, Long id)` 추가.
- `BrandRepositoryImpl`: 두 메서드 위임.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `BrandUpdateInfo`를 `BrandCreateInfo`와 별도로 둠 | 유스케이스별 `*Info` 네이밍 컨벤션. 둘 다 brandId만 담지만 의도 구분 | 공용 `BrandIdInfo` — BRD-4 커밋된 `BrandCreateInfo` 개명 유발, 이득 적음 |
