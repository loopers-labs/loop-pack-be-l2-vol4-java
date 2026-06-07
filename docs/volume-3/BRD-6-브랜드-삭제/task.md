# Task: BRD-6 브랜드 삭제 (+상품 cascade)

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`.
> 전제: Brand·Product aggregate 골격 존재. 본 시나리오 JPA 조회는 자체 추가(`findByBrandIdAndDeletedAtIsNull`)/기존 재사용(브랜드 `findByIdAndDeletedAtIsNull`) — PRD-6·PRD-7의 Product 추가에 코드 의존 없음.

## Phase 1: 도메인·인프라 (멱등 브랜드 조회 + 상품 cascade)

- [X] T001 `BrandRepository.findActiveById(Long)`(Optional, 부재·삭제 시 empty — 멱등 no-op용. 기존 `getActiveById` throw와 구분) 추가 — `main/.../domain/brand/BrandRepository.java`
- [X] T002 `BrandRepositoryImpl.findActiveById`: 기존 `findByIdAndDeletedAtIsNull(id)` 그대로 위임(Optional 반환) — `main/.../infrastructure/brand/BrandRepositoryImpl.java`
- [X] T003 `ProductRepository.findActiveByBrandId(Long brandId)`(List 반환, 소속 활성 상품 조회. 삭제는 Facade가 조율 — review 결정) 추가 — `main/.../domain/product/ProductRepository.java`
- [X] T004 `ProductJpaRepository.findByBrandIdAndDeletedAtIsNull(Long brandId)` + `ProductRepositoryImpl.findActiveByBrandId`: 그대로 위임(List 반환) — `main/.../infrastructure/product/ProductJpaRepository.java`, `main/.../infrastructure/product/ProductRepositoryImpl.java`
- [X] T005 통합 테스트 보강 — `test/.../infrastructure/brand/BrandRepositoryIntegrationTest.java`(FindActiveById: 활성 present / 삭제·부재 empty). (review 결정: `ProductRepository.deleteActiveByBrandId`의 레포지토리 단독 통합 테스트는 제외 — 로드 후 `forEach(delete)`는 enclosing 트랜잭션 안에서만 flush되어 `@Transactional` 없는 통합 테스트로는 durability 검증이 애매하고 컨벤션상 통합 테스트에 `@Transactional` 금지. cascade durability·격리는 `DeleteBrand` E2E가 실제 Facade 트랜잭션으로 커버.)

## Phase 2: 삭제 유스케이스 (`DELETE /api-admin/v1/brands/{brandId}`)

- [X] T006 `BrandFacade`에 `ProductRepository` 주입 + `deleteBrand(brandId)`(void, `findActiveById(id).ifPresent(brand -> { brand.delete(); productRepository.findActiveByBrandId(id).forEach(ProductModel::delete); })`) 추가 + 단위 테스트 — `main/.../application/brand/BrandFacade.java`, `test/.../application/brand/BrandFacadeTest.java`(DeleteBrand nested: 활성 브랜드 → brand·소속 상품 모두 deletedAt 기록 / 미존재·이미 삭제 → no-op(findActiveByBrandId 미호출))
- [X] T007 `BrandAdminV1ApiSpec.deleteBrand` 선언 추가(`ApiResponse<Void> deleteBrand(Long brandId)`, `@Operation`) — `main/.../interfaces/api/brand/BrandAdminV1ApiSpec.java`
- [X] T008 `BrandAdminV1Controller` `@DeleteMapping("/{brandId}")` `deleteBrand(@PathVariable Long brandId)` 추가(200 OK, `ApiResponse.success()`, 인증 파라미터 없음) — `main/.../interfaces/api/brand/BrandAdminV1Controller.java`
- [X] T009 E2E 테스트 — `test/.../interfaces/api/BrandAdminV1ApiE2ETest.java`(DeleteBrand nested: 정상 200+meta.result SUCCESS+브랜드·소속 상품 활성 조회 제외 / 다른 브랜드 상품 잔존 / 미존재 200 / 동일 요청 반복 200 / admin 헤더 없음 403. statusCode+meta.result+errorCode까지, 메시지 비단언. fixture는 Brand·Product JpaRepository.save 직접. cascade 검증은 ProductJpaRepository로 deletedAt 확인)

## Phase 3: 마무리

- [X] T010 spec 테스트 계획 대비 누락 점검(Facade cascade·no-op 분기 / Brand·Product Integration / E2E 5분기·다른 브랜드 미영향 매핑)
- [X] T011 `.http` — `http/commerce-api/brand-admin-v1.http`(삭제 요청 샘플 추가: 정상(cascade) / 반복 멱등 / 미존재 / admin 누락)
