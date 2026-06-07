# Task: PRD-7 상품 삭제

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`.
> 전제: PRD-6에서 `ProductJpaRepository.findByIdAndDeletedAtIsNull`이 추가되어 있다(재사용).

## Phase 1: 도메인·인프라 (멱등 조회 추가)

- [X] T001 `ProductRepository.findActiveById(Long)`(Optional, 부재·삭제 시 empty — 멱등 no-op용) 추가 — `main/.../domain/product/ProductRepository.java`
- [X] T002 `ProductRepositoryImpl.findActiveById`: `productJpaRepository.findByIdAndDeletedAtIsNull(id)` 그대로 위임(Optional 반환) — `main/.../infrastructure/product/ProductRepositoryImpl.java`
- [X] T003 `ProductRepository` 통합 테스트 보강 — `test/.../infrastructure/product/ProductRepositoryIntegrationTest.java`(FindActiveById nested: 활성 present / 삭제·부재 empty)

## Phase 2: 삭제 유스케이스 (`DELETE /api-admin/v1/products/{productId}`)

- [X] T004 `ProductFacade.deleteProduct(productId)` 추가(void, `findActiveById(id).ifPresent(ProductModel::delete)`) + 단위 테스트 — `main/.../application/product/ProductFacade.java`, `test/.../application/product/ProductFacadeTest.java`(DeleteProduct nested: 활성 → `delete()` 호출(deletedAt 기록) / 미존재 → no-op(delete 미호출, 예외 없음) / 이미 삭제 → no-op)
- [X] T005 `ProductAdminV1ApiSpec.deleteProduct` 선언 추가(`ApiResponse<Void> deleteProduct(Long productId)`, `@Operation`) — `main/.../interfaces/api/product/ProductAdminV1ApiSpec.java`
- [X] T006 `ProductAdminV1Controller` `@DeleteMapping("/{productId}")` `deleteProduct(@PathVariable Long productId)` 추가(200 OK, `ApiResponse.success()`, 인증 파라미터 없음) — `main/.../interfaces/api/product/ProductAdminV1Controller.java`
- [X] T007 E2E 테스트 — `test/.../interfaces/api/ProductAdminV1ApiE2ETest.java`(DeleteProduct nested: 정상 200+meta.result SUCCESS+활성 조회 제외 / 동일 요청 반복 200 / 미존재 200 / admin 헤더 없음 403. statusCode+meta.result+errorCode까지, 메시지 비단언. fixture는 ProductJpaRepository.save 직접)

## Phase 3: 마무리

- [X] T008 spec 테스트 계획 대비 누락 점검(Facade 멱등 3분기 / Integration findActiveById / E2E 4분기 매핑)
- [X] T009 `.http` — `http/commerce-api/product-admin-v1.http`(삭제 요청 샘플 추가: 정상 / 반복 멱등 / admin 누락)
