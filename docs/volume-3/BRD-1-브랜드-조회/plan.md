# Plan: BRD-1 브랜드 조회 (public 단건)

**Spec**: ./spec.md
**작성일**: 2026-05-25

## 요약

`GET /api/v1/brands/{brandId}`로 무인증 단건 조회를 제공한다. Brand 도메인 첫 public 표현 계층(`BrandV1Controller`)을 신설하고, `BrandFacade.readBrand`(단건, 활성 보장)가 `BrandInfo`를 반환한다. 이 `readBrand` 유스케이스와 `BrandInfo`는 BRD-3(admin 상세)와 공유하며, 노출 필드 차이는 DTO에서만 가른다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 추가 의존성: 없음

## 컨벤션·결정 점검
- [x] 호출 방향 interfaces → application → domain → infrastructure
- [x] CRUD 네이밍: `readBrand`
- [x] 무인증 — `/api/v1/**`는 인터셉터 가드 대상 아님(admin만 `/api-admin/**`)
- [x] 결정 7(soft delete): 활성 단건만 노출
- [x] 엔티티 비노출: Facade가 `BrandInfo`로 변환, public DTO가 필드 제한

## 레이어별 설계 결정 & 파일 맵

### interfaces (신규 public 표현 계층)
- `interfaces/api/brand/BrandV1Controller.java` (신규) — `@RequestMapping("/api/v1/brands")`. `@GetMapping("/{brandId}")` `readBrand(@PathVariable Long brandId)` → `brandFacade.readBrand(brandId)` → `ApiResponse.success(ReadResponse.from(info))`. 인증 파라미터 없음.
- `interfaces/api/brand/BrandV1Dto.java` (신규) — `ReadResponse(Long brandId, String name, String description)` + `from(BrandInfo)` (시각 필드 미노출).
- `interfaces/api/brand/BrandV1ApiSpec.java` (신규) — `@Tag`/`@Operation`.

### application
- `BrandFacade.readBrand(Long brandId)` (신규, `@Transactional(readOnly = true)`): `brandRepository.getActiveById(brandId)` → `BrandInfo.from(brand)`. (BRD-3 admin 상세도 이 메서드 사용)
- `application/brand/BrandInfo.java` (신규, 공유) — `record(Long brandId, String name, String description, ZonedDateTime createdAt, ZonedDateTime updatedAt)` + `from(BrandModel)`.

### domain (편집)
- `BrandRepository.getActiveById(Long id)` (신규, 없으면 `CoreException(NOT_FOUND)` — find/get 컨벤션, BRD-3/5 공유).

### infrastructure (편집)
- `BrandJpaRepository.findByIdAndDeletedAtIsNull(Long id)` (신규).
- `BrandRepositoryImpl.getActiveById`: `findByIdAndDeletedAtIsNull(id).orElseThrow(NOT_FOUND)`.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `BrandInfo`에 시각 포함, public DTO가 subset만 노출 | 단건 read 유스케이스를 BRD-1/3가 공유. 노출 제한은 DTO 경계 책임 | public/admin Info 2벌 — 동일 데이터 중복 |
