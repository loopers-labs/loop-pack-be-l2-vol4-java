# Plan: BRD-3 브랜드 상세 (admin)

**Spec**: ./spec.md
**작성일**: 2026-05-25

## 요약

`GET /api-admin/v1/brands/{brandId}`로 관리자가 브랜드 상세를 조회한다. BRD-1과 동일한 `BrandFacade.readBrand`(단건, 활성 보장) 유스케이스를 재사용하고, admin DTO가 등록·갱신 시각까지 노출한다. 신규 도메인/응용 로직은 없고 admin 표현 계층만 추가한다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 추가 의존성: 없음

## 컨벤션·결정 점검
- [x] 호출 방향 준수
- [x] CRUD 네이밍: `readBrand`(컨트롤러), Facade 메서드는 BRD-1과 공유
- [x] admin 인증: `/api-admin/**` 인터셉터 가드
- [x] 결정 7: 활성 단건만 노출(부재/삭제 404)
- [x] public/admin 응답 shape 분리: admin DTO는 시각 포함

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `BrandAdminV1Controller`에 `@GetMapping("/{brandId}")` `readBrand(@PathVariable Long brandId)` 추가 → `brandFacade.readBrand(brandId)` → `ApiResponse.success(DetailResponse.from(info))`.
- `BrandAdminV1Dto`에 `DetailResponse(Long brandId, String name, String description, ZonedDateTime createdAt, ZonedDateTime updatedAt)` + `from(BrandInfo)` 추가.
- `BrandAdminV1ApiSpec`에 `readBrand`(상세) 선언 추가.

### application
- **신규 없음** — `BrandFacade.readBrand`(BRD-1에서 신설) 재사용. `BrandInfo`(시각 포함) 그대로 활용.

### domain / infrastructure
- **신규 없음** — `getActiveById`(BRD-1/5에서 신설) 재사용.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| readBrand 유스케이스를 BRD-1과 공유, DTO만 분리 | 동일 "단건 조회"를 중복 구현하지 않음. 시각 노출 여부는 DTO 경계 책임 | admin 전용 read 메서드 별도 — 중복 |
