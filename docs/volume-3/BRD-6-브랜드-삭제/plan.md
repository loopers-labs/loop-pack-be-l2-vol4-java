# Plan: BRD-6 브랜드 삭제 (+상품 cascade)

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

`DELETE /api-admin/v1/brands/{brandId}`로 관리자가 브랜드를 멱등 soft delete 하고, 같은 트랜잭션 안에서 소속 활성 상품을 cascade soft delete 한다. `BrandFacade.deleteBrand`가 `BrandRepository.findActiveById`(Optional)로 조회해 활성 브랜드일 때만 `brand.delete()` 후 `productRepository.findActiveByBrandId(brandId).forEach(ProductModel::delete)`로 소속 활성 상품을 지운다(부재·이미 삭제면 no-op). **삭제 조율은 Facade가 두 도메인 모두 도메인 `delete()`로 일관되게 수행**하고, Repository는 조회만 제공한다(브랜드는 Facade·상품은 Repository에서 지우던 비대칭 해소 — review 결정). cascade는 DB 제약이 아니라 응용 계층이 Brand·Product 두 저장소를 한 트랜잭션으로 조율한다 — `ProductFacade`가 `BrandRepository`를 참조한 선례와 동형. admin 인증은 `AdminAuthInterceptor`가 가드.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검
- [x] 호출 방향 interfaces → application → domain → infrastructure 준수. cross-domain은 application이 타 도메인 Repository를 주입(ProductFacade→BrandRepository 선례)
- [x] CRUD 네이밍: Facade·Controller 메서드 `deleteBrand`
- [x] admin 인증: `/api-admin/**` 인터셉터가 가드(컨트롤러 인증 파라미터 없음)
- [x] 결정 6(멱등 삭제): 부재·이미 삭제 모두 정상 응답. no-op 분기를 위해 `BrandRepository.findActiveById`(Optional) 사용
- [x] 결정 7(soft delete): 브랜드·상품 모두 `BaseEntity.delete()`(멱등)
- [x] 트랜잭션: `BrandFacade` 클래스 레벨 `@Transactional`로 브랜드 삭제 + 상품 cascade가 한 거래 단위(부분 삭제 방지)
- [x] cascade 범위: 대상 브랜드의 `deletedAt IS NULL` 상품만. 다른 브랜드·이미 삭제 상품 미영향
- [x] 응답 shape: `ApiResponse.success()`(Void, data null) — 200 OK

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `interfaces/api/brand/BrandAdminV1Controller.java` (편집) — `@DeleteMapping("/{brandId}")` `deleteBrand(@PathVariable Long brandId)` 추가. 200 OK. `brandFacade.deleteBrand(brandId)` 후 `ApiResponse.success()`(`ApiResponse<Void>`).
- `interfaces/api/brand/BrandAdminV1ApiSpec.java` (편집) — `ApiResponse<Void> deleteBrand(Long brandId)` `@Operation` 선언 추가.
- `BrandAdminV1Dto`: 변경 없음(요청 본문·응답 데이터 없음).

### application (편집)
- `application/brand/BrandFacade.java` (편집) — `ProductRepository` 주입 추가. `deleteBrand(Long brandId)` (신규, 반환 void): `brandRepository.findActiveById(brandId).ifPresent(brand -> { brand.delete(); productRepository.findActiveByBrandId(brandId).forEach(ProductModel::delete); })`. 클래스 레벨 `@Transactional`로 한 거래 — 브랜드·상품 모두 도메인 `delete()`를 Facade에서 호출(삭제 책임 일관, Repository는 조회만 — review 결정).

### domain (편집)
- `domain/brand/BrandRepository.java` (편집) — `Optional<BrandModel> findActiveById(Long id)` 추가(부재·삭제 시 `Optional.empty` — 멱등 no-op용. 기존 `getActiveById` throw와 구분).
- `domain/product/ProductRepository.java` (편집) — `List<ProductModel> findActiveByBrandId(Long brandId)` 추가(소속 활성 상품 조회. 삭제는 Facade가 도메인 `delete()`로 조율).
- `BrandModel`/`ProductModel`: 변경 없음(`BaseEntity.delete()` 사용).

### infrastructure (편집)
- `infrastructure/brand/BrandRepositoryImpl.java` (편집) — `findActiveById`: 기존 `findByIdAndDeletedAtIsNull(id)` 그대로 위임(Optional 반환).
- `infrastructure/brand/BrandJpaRepository.java` — 변경 없음(`findByIdAndDeletedAtIsNull` 이미 존재).
- `infrastructure/product/ProductRepositoryImpl.java` (편집) — `findActiveByBrandId`: `productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId)` 그대로 위임(List 반환). 삭제는 Facade가 조율.
- `infrastructure/product/ProductJpaRepository.java` (편집) — `List<ProductModel> findByBrandIdAndDeletedAtIsNull(Long brandId)` 추가.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| cascade를 Facade에서 `findActiveByBrandId` 로드 후 `forEach(ProductModel::delete)` 조율 | 브랜드·상품 삭제를 같은 레이어(Facade)에서 도메인 `delete()`로 일관 처리. Repository는 조회만 담당해 책임이 한 레이어에 숨지 않음. 도메인 `delete()` 멱등·`deletedAt` 일원화 유지. 관리자 삭제는 드문 작업이라 N쿼리 부담 무시 가능 (review 결정) | ① Repository에 `deleteActiveByBrandId`를 두고 내부에서 `forEach(delete)` — 브랜드는 Facade·상품은 Repo에서 지워 삭제 책임 비대칭 ② `@Modifying` 벌크 UPDATE 1쿼리 — `BaseEntity.delete()` 우회·영속성 컨텍스트 stale 위험 |
| cascade 조율을 `BrandFacade`가 `ProductRepository` 직접 주입으로 처리 | `ProductFacade`→`BrandRepository` 참조 선례와 동형(application이 타 도메인 Repository 주입). 시퀀스의 "브랜드→상품 일괄 삭제 요청"을 응용 계층에서 실현 | `BrandFacade`→`ProductFacade` 호출(facade 간 호출, 본 코드베이스에 선례 없음) |
