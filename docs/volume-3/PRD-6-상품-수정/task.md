# Task: PRD-6 상품 수정

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase 1: 도메인·인프라 (Product aggregate 확장)

- [X] T001 `ProductModel.update(rawName, rawDescription, rawPrice, rawStock)` 추가 + 단위 테스트 — `main/.../domain/product/ProductModel.java`, `test/.../domain/product/ProductModelTest.java`(Update nested: 이름·설명·가격·재고 갱신 / 새 이름 위반 BAD_REQUEST / 새 가격 -1 BAD_REQUEST / 새 재고 -1 BAD_REQUEST. brandId 미변경)
- [X] T002 `ProductRepository.getActiveById(Long)`(없으면 NOT_FOUND, find/get 컨벤션) 추가 — `main/.../domain/product/ProductRepository.java`
- [X] T003 `ProductJpaRepository.findByIdAndDeletedAtIsNull(Long)` + `ProductRepositoryImpl.getActiveById`(orElseThrow NOT_FOUND "상품이 존재하지 않습니다.") 위임 — `main/.../infrastructure/product/ProductJpaRepository.java`, `main/.../infrastructure/product/ProductRepositoryImpl.java`
- [X] T004 `ProductRepository` 통합 테스트 보강 — `test/.../infrastructure/product/ProductRepositoryIntegrationTest.java`(GetActiveById nested: 활성 상품 반환 / 삭제·부재 NOT_FOUND)

## Phase 2: 수정 유스케이스 (`PUT /api-admin/v1/products/{productId}`)

- [X] T005 `ProductUpdateInfo` 작성 — `main/.../application/product/ProductUpdateInfo.java`(`record(Long productId)` + `from(ProductModel)`)
- [X] T006 `ProductFacade.updateProduct(productId, name, description, price, stock)` 추가 + 단위 테스트 — `main/.../application/product/ProductFacade.java`, `test/.../application/product/ProductFacadeTest.java`(UpdateProduct nested: 대상 미존재/삭제 → NOT_FOUND / 정상 → `getActiveById` 후 update, productId 반환)
- [X] T007 `ProductAdminV1Dto.UpdateRequest`(name @NotBlank, description 무어노테이션, price @NotNull @PositiveOrZero, stock @NotNull @PositiveOrZero, **brandId 없음**) + `UpdateResponse(productId) + from(ProductUpdateInfo)` — `main/.../interfaces/api/product/ProductAdminV1Dto.java`
- [X] T008 `ProductAdminV1ApiSpec.updateProduct` 선언 추가 — `main/.../interfaces/api/product/ProductAdminV1ApiSpec.java`(`@Operation`)
- [X] T009 `ProductAdminV1Controller` `@PutMapping("/{productId}")` `updateProduct(@PathVariable Long productId, @Valid @RequestBody UpdateRequest)` 추가(200 OK, 인증 파라미터 없음) — `main/.../interfaces/api/product/ProductAdminV1Controller.java`
- [X] T010 E2E 테스트 — `test/.../interfaces/api/ProductAdminV1ApiE2ETest.java`(UpdateProduct nested: 정상 200+meta.result SUCCESS+productId+값 갱신 / admin 헤더 없음 403 / 대상 미존재 404 / 이름 101자 400 / 가격 -1 400. statusCode+meta.result+errorCode까지, 메시지 비단언. fixture는 ProductJpaRepository.save 직접)

## Phase 3: 마무리

- [X] T011 spec 테스트 계획 대비 누락 점검(ProductModel.update 4분기 / Facade 대상 분기 / Integration getActiveById / E2E 5분기 매핑)
- [X] T012 `.http` — `http/commerce-api/product-admin-v1.http`(수정 요청 샘플 추가: 정상 / admin 누락 / 대상 미존재 / 이름 범위 위반 / 가격 음수)
