# Plan: PRD-5 상품 등록

**Spec**: ./spec.md
**작성일**: 2026-05-26

## 요약

관리자가 `POST /api-admin/v1/products`로 상품을 등록한다. User·Brand 참조 구현을 그대로 본떠 Product aggregate(`ProductModel` + `Name`·`Price`·`Stock` VO + `ProductRepository`, 설명은 검증 없어 `String` 직접 보유)를 신설한다. `ProductFacade`가 기존 `BrandRepository.existsActiveById`로 브랜드 활성 여부를 선검사(없으면 NOT_FOUND)한 뒤 상품을 저장한다. admin 인증은 BRD-4에서 `/api-admin/**`에 등록한 `AdminAuthInterceptor`가 자동 가드하므로 추가 토대 없음.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (기존 의존성으로 충족)

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수 (Facade가 Repository 주입, 도메인 서비스 없음 — Brand 패턴)
- [x] 검증: 이름 길이(1~100)는 VO `Name.from()`이 단일 원천(DTO엔 길이 안 둠). 가격·재고 `≥0`은 VO(`Price`/`Stock.from()`)가 갖고, DTO에도 `@PositiveOrZero`로 1차 방어 — dto.md가 `@Positive`류 수치 가드를 DTO 1차 방어로 허용하므로 정합. DTO null 방어는 `@NotBlank`/`@NotNull`
- [x] 인증: admin은 순수 인가 게이트라 BRD-4의 `AdminAuthInterceptor`가 `/api-admin/**`를 가드. admin 컨트롤러에 인증 파라미터 없음 (추가 등록 불필요 — implement에서 확인)
- [x] 결정 3(재고 보관 위치 A): `stock`은 `products` 테이블 컬럼, `Stock` VO로 보유 (별도 Stock 테이블 미사용)
- [x] 결정 7(soft delete): `BaseEntity` 상속으로 `deleted_at` 확보 (등록 cycle에선 사용 안 함)
- [x] 브랜드 활성 검증: `BrandRepository.existsActiveById(brandId)` 재사용 — false면 Facade가 NOT_FOUND (get 결과를 버리지 않고 존재 확인 의도를 명시 — review 결정)
- [x] 가격 VO: 공용 `Money` 대신 Product 전용 `Price` VO (spec 결정). 가격 검증은 등록 원천 1회
- [x] 설명: `description`은 검증·행위가 없어 VO 없이 `String` 컬럼 직접 보유, 값 그대로 저장(null 허용) (BRD-4 선례)
- [x] stock 동작 범위: `Stock` VO는 생성·검증만. `decrease()`/`isAvailable()`는 ORD-1·PRD-1로 미룸

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/product/ProductAdminV1Controller.java` (신규) — `POST /api-admin/v1/products`, `@ResponseStatus(CREATED)`. 시그니처: `createProduct(@Valid @RequestBody ProductAdminV1Dto.CreateRequest request)` (인증 파라미터 없음 — Interceptor가 경로 가드). `ProductFacade.createProduct(brandId, name, description, price, stock)` 호출 후 `ApiResponse.success(CreateResponse.from(info))`.
- `interfaces/api/product/ProductAdminV1Dto.java` (신규)
  - `CreateRequest(Long brandId, String name, String description, Integer price, Integer stock)`: `brandId`·`price`·`stock`에 `@NotNull`(message 명시), `price`·`stock`에 `@PositiveOrZero`(message 명시, review 결정), `name`에 `@NotBlank`(message 명시). `description`은 선택 필드라 무어노테이션. (이름 길이는 DTO에서 검증하지 않음 — `Name` VO 단일 원천)
  - `CreateResponse(Long productId)`: `from(ProductCreateInfo)`.
- `interfaces/api/product/ProductAdminV1ApiSpec.java` (신규) — SpringDoc `@Tag`/`@Operation`.

### application
- `application/product/ProductFacade.java` (신규) — `@Service @Transactional`. 주입: `BrandRepository`, `ProductRepository`. `createProduct(Long brandId, String name, String description, Integer price, Integer stock)`: `!brandRepository.existsActiveById(brandId)`이면 `CoreException(NOT_FOUND)`; 통과하면 `ProductModel.builder()...build()` 후 `save`, `ProductCreateInfo.from(saved)` 반환.
- `application/product/ProductCreateInfo.java` (신규) — `record ProductCreateInfo(Long productId)` + `from(ProductModel)`.

### domain
- `domain/product/ProductModel.java` (신규) — `@Entity @Table(name="products")` extends `BaseEntity`. 필드: `@Column(name="brand_id", nullable=false) Long brandId`, `@Embedded Name name`, `@Column(name="description", columnDefinition="TEXT") String description`, `@Embedded Price price`, `@Embedded Stock stock`. `@NoArgsConstructor(PROTECTED)`+`@AllArgsConstructor(PROTECTED)`. `private @Builder ProductModel(Long brandId, String rawName, String rawDescription, Integer rawPrice, Integer rawStock)` → `this.brandId = brandId; this.name = Name.from(rawName); this.description = rawDescription; this.price = Price.from(rawPrice); this.stock = Stock.from(rawStock)`. (update/delete/decreaseStock/isStockAvailable는 후속 cycle — 이번 범위 밖.)
- VO:
  - `domain/product/Name.java` (신규) — `record @Embeddable`, `@Column(name="name", nullable=false, length=100) String value`. `from(value)`: null/blank → BAD_REQUEST; 길이 `MIN_LENGTH(1)`~`MAX_LENGTH(100)` 위반 → BAD_REQUEST(String.format).
  - `domain/product/Price.java` (신규) — `record @Embeddable`, `@Column(name="price", nullable=false) Integer value`. `from(value)`: null → BAD_REQUEST; `< MIN_VALUE(0)` → BAD_REQUEST.
  - `domain/product/Stock.java` (신규) — `record @Embeddable`, `@Column(name="stock", nullable=false) Integer value`. `from(value)`: null → BAD_REQUEST; `< MIN_QUANTITY(0)` → BAD_REQUEST. (decrease/isAvailable 미도입.)
- `domain/product/ProductRepository.java` (신규) — `ProductModel save(ProductModel product)`. (조회 계열은 후속 cycle.)
- 도메인 서비스: **없음** (브랜드 검증은 `BrandRepository` 재사용, Facade 책임 — Brand 패턴).

### infrastructure
- `infrastructure/product/ProductRepositoryImpl.java` (신규) — `@Component`, `ProductJpaRepository` 위임. `save`.
- `infrastructure/product/ProductJpaRepository.java` (신규) — `extends JpaRepository<ProductModel, Long>`. 등록만이라 파생 쿼리 없음.

### support (편집)
- 없음 — `ErrorType.BAD_REQUEST`·`NOT_FOUND`·`FORBIDDEN` 모두 기존 존재. `AdminAuthInterceptor`가 `/api-admin/**`에 이미 등록되어 products 경로 자동 가드(implement에서 재확인).

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `Price`·`Stock`의 값 타입을 `Integer`로 (클래스 다이어그램 `Long` → ERD `INT` 따름) | ERD 물리 스키마가 `price`·`stock`을 `INT`로 정의. `Integer`는 INT로 매핑되어 컬럼 타입 일치. `Long`은 BIGINT로 매핑되어 ERD와 불일치 | `Long` 사용(클래스 다이어그램 표기 그대로 — ERD와 컬럼 타입 어긋남) |
| 가격을 공용 `Money` 대신 Product 전용 `Price` VO | 검증은 값이 처음 들어오는 등록 시점 1회로 충분. Order·OrderItem 단가는 검증된 스냅샷이라 재검증 불필요. 공용 추상화는 현 시점 YAGNI | 공용 `Money` VO 도입(Order가 아직 없어 사용처 단일, 조기 추상화) |
| admin 컨트롤러를 `ProductAdminV1Controller`로 분리 명명 (Facade는 단일) | `/api-admin/v1/*`(admin)와 `/api/v1/*`(public)는 경로·인증·응답 shape가 다름. 모든 admin cycle의 명명 선례(BRD-4) | public/admin 한 컨트롤러 혼재(경계 흐려짐) |
