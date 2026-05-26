# Task: PRD-5 상품 등록

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase F: Foundational (Product aggregate 골격)

- [X] T001 `Name` VO 작성 + 단위 테스트 — `main/.../domain/product/Name.java`, `test/.../domain/product/NameTest.java` (1자·100자 통과 / 빈 문자열·101자 BAD_REQUEST, `MIN_LENGTH=1`·`MAX_LENGTH=100` 상수)
- [X] T002 `Price` VO 작성 + 단위 테스트 — `main/.../domain/product/Price.java`, `test/.../domain/product/PriceTest.java` (`record @Embeddable`, `@Column(name="price", nullable=false) Integer value`; 0 통과 / -1·null BAD_REQUEST, `MIN_VALUE=0` 상수)
- [X] T003 `Stock` VO 작성 + 단위 테스트 — `main/.../domain/product/Stock.java`, `test/.../domain/product/StockTest.java` (`record @Embeddable`, `@Column(name="stock", nullable=false) Integer value`; 0 통과 / -1·null BAD_REQUEST, `MIN_QUANTITY=0` 상수. decrease/isAvailable 미도입)
- [X] T004 설명(description)은 `ProductModel`의 `String @Column(columnDefinition="TEXT")` 필드로 직접 보유 — 검증·행위가 없어 VO 미도입(별도 파일·VO 단위 테스트 없음). 보존은 T005·T010에서 검증.
- [X] T005 `ProductModel` 작성 + 단위 테스트 — `main/.../domain/product/ProductModel.java`, `test/.../domain/product/ProductModelTest.java` (`@Entity @Table(name="products")` extends BaseEntity, `@Column(name="brand_id") Long brandId`, `@Embedded` Name/Price/Stock, `String description`, `private @Builder(brandId, rawName, rawDescription, rawPrice, rawStock)`, 생성 시 모든 필드 보유 / null 설명 허용)
- [X] T006 `ProductRepository` 인터페이스 — `main/.../domain/product/ProductRepository.java` (`ProductModel save(ProductModel)`)
- [X] T007 `ProductJpaRepository` + `ProductRepositoryImpl` — `main/.../infrastructure/product/ProductJpaRepository.java`(`extends JpaRepository<ProductModel, Long>`), `main/.../infrastructure/product/ProductRepositoryImpl.java`(`@Component`, save 위임)
- [X] T008 `ProductRepository` 통합 테스트 — `test/.../infrastructure/product/ProductRepositoryIntegrationTest.java` (저장 후 식별자 부여·필드 보존 조회)

## Phase 1: 구현 (등록 유스케이스)

- [X] T009 `ProductCreateInfo` 작성 — `main/.../application/product/ProductCreateInfo.java` (`record(Long productId)` + `from(ProductModel)`)
- [X] T010 `ProductFacade.createProduct` 작성 + 단위 테스트 — `main/.../application/product/ProductFacade.java`, `test/.../application/product/ProductFacadeTest.java` (`BrandRepository`·`ProductRepository` 주입; 브랜드 미존재/삭제 → NOT_FOUND / 정상 → 저장 후 productId 반환. 브랜드 검증은 `!brandRepository.existsActiveById(brandId)` 시 NOT_FOUND)
- [X] T011 `ProductAdminV1Dto` 작성 — `main/.../interfaces/api/product/ProductAdminV1Dto.java` (`CreateRequest(brandId @NotNull, name @NotBlank, description 무어노테이션, price @NotNull @PositiveOrZero, stock @NotNull @PositiveOrZero)`, `CreateResponse(productId) + from`)
- [X] T012 `ProductAdminV1ApiSpec` 작성 — `main/.../interfaces/api/product/ProductAdminV1ApiSpec.java` (`@Tag`/`@Operation`)
- [X] T013 `ProductAdminV1Controller` 작성 — `main/.../interfaces/api/product/ProductAdminV1Controller.java` (`POST /api-admin/v1/products`, `@ResponseStatus(CREATED)`, `@Valid @RequestBody`, 인증 파라미터 없음)
- [X] T014 E2E 테스트 — `test/.../interfaces/api/ProductAdminV1ApiE2ETest.java` (정상 201+meta.result SUCCESS+productId / admin 헤더 없음 403 / 브랜드 미존재 404 / 이름 101자 400 / 가격 -1 400. statusCode+meta.result+errorCode까지, 메시지 비단언. fixture는 BrandJpaRepository.save 직접으로 활성 브랜드 준비)

## Phase 2: 마무리

- [X] T015 spec 테스트 계획 대비 누락 점검 (VO 3종 경계 / Product 보유 / Facade 브랜드 분기 / Integration 저장·조회 / E2E 5분기 매핑) 및 `AdminAuthInterceptor`가 `/api-admin/v1/products`를 가드하는지(추가 등록 불필요한지) 확인
- [X] T016 `.http` 파일 — `http/commerce-api/product-admin-v1.http` (상품 등록 요청 샘플: 정상 / admin 헤더 누락 / 브랜드 미존재 / 이름 범위 위반 / 가격 음수)
