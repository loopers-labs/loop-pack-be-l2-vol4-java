# 05. Architecture Decisions — 아키텍처 결정 기록

> 이 문서는 구현 전 설계 단계에서 내린 아키텍처 결정과 그 이유를 기록합니다.
> "왜 이렇게 했지?"를 나중에 떠올릴 때 참조하세요.

---

## 결정 1. 레이어드 아키텍처 (4티어)

**결정**
```
interfaces → application → domain ← infrastructure
```

**이유**
- 레이어 간 의존 방향을 단방향으로 강제해 변경 영향 범위를 제한한다.
- Application Layer는 도메인 객체를 조합하는 orchestration 역할만 수행한다 (thin).
- 핵심 비즈니스 로직은 Domain Layer(Model, Service)에 위치한다.

---

## 결정 2. 패키지 전략 — Domain-first (약하게)

**결정**
```
com.loopers/
├── {domain}/           ← 최상위는 도메인 (user, brand, product, like, order)
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── interfaces/
└── support/            ← 도메인 무관 공통 코드
```

**이유**
- 기존에 Layer-first 경험이 충분하므로 Domain-first를 직접 경험해 본다.
- 한 도메인의 관련 코드가 한 패키지 안에 모여 파편화가 줄어든다.

**약하게 선택한 이유 (레이어 서브패키지 유지)**
- 단일 Gradle 모듈이라 빌드 시스템이 레이어 경계를 강제하지 않는다.
- 레이어 서브패키지를 유지하면 import 경로만 봐도 레이어 침범이 눈에 띈다.
  ```java
  // product/interfaces/ProductV1Controller.java 에서
  import com.loopers.product.infrastructure.ProductRepositoryImpl; // 위반이 바로 보임
  ```
- 강하게 가면 레이어 경계를 개발자 규율에만 의존해야 한다.

**트레이드오프**
- Layer-first 대비 도메인 패키지 간 의존 관계가 코드 레벨에서 생길 수 있다.
- 크로스 도메인 로직의 위치 결정이 필요하다. (→ 결정 4 참고)

---

## 결정 3. DIP 적용 범위

**결정**
- 적용: `Repository` 인터페이스, `PasswordEncryptor` 인터페이스
- 미적용: `Facade`, `DomainService` 등

**이유**
- DIP의 장점은 구현체를 교체할 때만 실현된다.
- Repository는 단위 테스트에서 Fake 구현체로 교체할 수 있어야 한다.
- PasswordEncryptor는 테스트에서 NoOp 구현체로 교체하기 위해 인터페이스가 필요하다.
- Facade, DomainService는 교체할 시나리오가 없으므로 인터페이스 없이 구체 클래스를 직접 사용한다.

**트레이드오프**
- 불필요한 인터페이스를 제거해 파일 수와 관리 비용을 줄인다.
- 단, 나중에 교체 가능성이 생기면 인터페이스를 추가해야 한다.

---

## 결정 4. 크로스 도메인 로직 위치

**결정**
- 여러 도메인에 걸친 협력 로직은 Application Layer(Facade)에서 처리한다.
- 각 도메인 서비스는 자신의 도메인만 담당하고, Facade가 순서를 조율한다.

**이유**
- Domain 레이어가 다른 도메인의 Service/Repository를 직접 참조하면 의존이 뒤얽힌다.
- Facade에서 조율하면 각 도메인 서비스는 단순하게 유지되고, 흐름은 한 곳에서 읽을 수 있다.

**예시 — 브랜드 삭제 연쇄 처리**

```java
// brand/application/BrandFacade.java
@Transactional
public void deleteBrand(Long brandId) {
    productService.deleteByBrandId(brandId);   // 상품 소프트 딜리트
    userService.deleteByBrandId(brandId);      // BRAND_ADMIN 소프트 딜리트
    brandService.delete(brandId);              // 브랜드 소프트 딜리트
}
```

---

## 결정 5. `likeCount` 업데이트 방식

**결정**
- Repository 원자 쿼리로 처리한다.

```java
// product/domain/ProductRepository.java
void incrementLikeCount(Long productId);
void decrementLikeCount(Long productId);

// product/infrastructure/ProductRepositoryImpl.java
@Modifying
@Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
void incrementLikeCount(@Param("id") Long productId);
```

**이유**
- 동시 좋아요 요청 시 `likeCount++` (Java 필드 수정 + JPA dirty checking)은 Lost Update가 발생한다.
- `UPDATE SET like_count = like_count + 1`은 DB가 원자적으로 처리해 동시성이 안전하다.

**파생 결정**
- 클래스 다이어그램의 `ProductModel.likeAdded()` / `likeRemoved()`는 구현하지 않는다.
  필드를 직접 수정하지 않으므로 메서드 존재 이유가 없다.

---

## 결정 6. `SoftDeletableEntity` 분리

**결정**
- `BaseEntity`(id, createdAt, updatedAt)와 `SoftDeletableEntity`(+ deletedAt)를 분리한다.

| 상속 | 도메인 |
|------|--------|
| `SoftDeletableEntity` | UserModel, BrandModel, ProductModel |
| `BaseEntity` | LikeModel, OrderModel, OrderItemModel |

**이유**
- `LikeModel`, `OrderModel`, `OrderItemModel`은 소프트 딜리트 시나리오가 없다.
- 불필요한 `deletedAt` 컬럼을 갖지 않도록 한다.
- ERD 설계 의도와 일치한다.

**현실적 영향**
- 현재 `modules/jpa/BaseEntity`에 `deletedAt`이 포함되어 있어 수정이 필요하다.

---

## 결정 7. Admin/Customer API 분리 방식

**결정**
- 같은 `interfaces/` 서브패키지 안에서 파일명으로 구분한다.
  - 고객: `BrandV1Controller.java`
  - 어드민: `AdminBrandV1Controller.java`

**이유**
- 서브패키지(`customer/`, `admin/`)로 나누면 `interfaces/` 안에서 또 파편화가 생긴다.
- 파일명만으로 충분히 구분이 가능하다.

---

## 결정 8. `OrderItemModel` 위치

**결정**
- `order/domain/` 안에 포함한다.

**이유**
- `OrderItemModel`은 `OrderModel` 없이 독립적으로 존재하지 않는다.
- 클래스 다이어그램에서 컴포지션(`*--`)으로 표현된 관계다.
- 별도 패키지로 분리하면 불필요한 파편화가 생긴다.

---

## 결정 9. 도메인 간 의존 원칙 및 결합 허용 여부

**핵심 질문**
> 도메인 간 의존을 어느 레이어에서 어떻게 허용할 것인가?

**고민한 배경**

BrandFacade(brand/application)가 ProductService(product/domain)를 호출하는 상황에서,
ProductModel(product/domain)이 BrandModel(brand/domain)을 `@ManyToOne`으로 참조하면
두 패키지가 서로를 아는 상태가 된다.

```
product/domain    → brand/domain    (ProductModel → BrandModel)
brand/application → product/domain  (BrandFacade → ProductService)
```

이것이 순환인가 검토했다.
`brand/domain`은 `product`를 모르기 때문에 사이클이 닫히지 않아 기술적 순환은 아니다.
다만 두 패키지가 서로를 의식하는 **양방향 인지(bidirectional awareness)** 가 생긴다.

**결합을 끊는 방법 (Long brandId만 저장)**

```java
class ProductModel {
    private Long brandId;  // BrandModel 대신 ID만
}
// ProductFacade에서 IN 쿼리로 브랜드 일괄 조회 후 조합
Map<Long, BrandModel> brandMap = brandService.findAllByIds(brandIds);
```

장점: product/domain이 brand/domain을 전혀 모름 → 도메인 완전 독립, 단방향 의존만 남음
단점: 상품 목록 조회 시 Facade에서 브랜드 정보를 직접 조합해야 함

**결합을 허용하는 방법 (@ManyToOne 객체 참조)**

```java
class ProductModel {
    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "brand_id", foreignKey = @ForeignKey(NO_CONSTRAINT))
    private BrandModel brand;
}
```

장점: JOIN FETCH 한 번으로 상품 + 브랜드 함께 조회. 코드가 단순하다.
단점: product/domain이 brand/domain을 import → 양방향 인지 발생

**결정: 결합 허용 → 원칙 확정**

요구사항 관점에서 결합 허용을 결정했고, 이를 통해 도메인 간 의존 원칙이 확정됐다.

- 상품 목록(20개) 카드에 브랜드명이 항상 함께 표시된다.
- 브랜드 필터와 정렬이 함께 동작한다.
- Product와 Brand는 이 서비스에서 항상 함께 다뤄지는 도메인이다.
- 도메인 독립성을 위해 Facade에 조합 로직을 추가하는 것보다, 요구사항에 맞게 결합을 허용하고 코드를 단순하게 유지하는 것이 낫다.

**확정된 원칙**
- Domain 레이어: 자기 도메인 로직만 담당. 다른 도메인의 Service/Repository 직접 참조 금지.
- Application 레이어: 도메인 간 협력이 일어나는 유일한 곳.
- 예외: JPA `@ManyToOne` 구조적 참조는 Domain 레이어에서도 허용.
- `Application → Domain ← Infrastructure` 레이어 원칙은 위반하지 않는다.
  `product/domain → brand/domain`은 같은 Domain 레이어 안의 수평 의존이며,
  레이어 원칙은 레이어 간 수직 의존 방향만 다룬다.

**전체 의존 방향 지도**

```
[Domain 레이어 — 수평 의존]
product/domain ──(@ManyToOne)──► brand/domain

[Application 레이어 — 도메인 간 조합]
like/application  ──► product/domain  (likeCount 업데이트, 좋아요 목록 상품 조회)
order/application ──► product/domain  (재고 차감, 스냅샷)
brand/application ──► product/domain  (브랜드 삭제 연쇄)
brand/application ──► user/domain     (브랜드 삭제 연쇄)
```

**유사 관계 검토 — User(BRAND_ADMIN) → Brand**

Product → Brand와 동일한 패턴이 User → Brand에도 존재한다.
BRAND_ADMIN은 브랜드에 귀속되고, 브랜드 삭제 시 함께 소프트 딜리트된다.
그러나 UserModel은 `Long brandId`만 저장하고 `@ManyToOne BrandModel`을 갖지 않는다.

| | Product → Brand | User → Brand |
|--|--|--|
| 관계 | 상품은 브랜드에 소속 | BRAND_ADMIN은 브랜드에 귀속 |
| 브랜드 삭제 시 | 상품 소프트 딜리트 | BRAND_ADMIN 소프트 딜리트 |
| 구현 | `@ManyToOne BrandModel` | `Long brandId` |

구현이 다른 이유: Product는 조회 시 브랜드명 표시가 필요해 BrandModel 객체가 필요하고,
User는 자신의 브랜드인지 ID 비교만 하면 되므로 `Long brandId`로 충분하다.

---

## 결정 10. 내 좋아요 목록 조회 — 상품 정보 조회 방식

**상황**
`LikeModel`은 `productId(Long)`만 갖는다.
좋아요 목록 조회 시 각 상품 정보가 필요하므로 `like/application → product/domain` 의존이 생긴다.

**선택지**

- A안: `productService.findById()`를 하나씩 호출 → N+1 발생 (페이지당 최대 N번 쿼리)
- B안: productId 목록으로 `findAllByIds()` IN 쿼리 → 페이지당 쿼리 2번으로 고정

**결정: B안 (IN 쿼리)**

오프셋 페이지네이션으로 한 페이지가 고정되더라도 페이지 요청마다 N번 vs 2번 차이가 난다.
`productService.findAllByIds(List<Long>)` 메서드 하나 추가로 해결되므로 B안을 선택한다.

```java
// like/application/LikeFacade.java
List<LikeModel> likes = likeService.findByUserId(userId, pageable);

List<Long> productIds = likes.stream()
    .map(LikeModel::getProductId)
    .toList();

List<ProductModel> products = productService.findAllByIds(productIds);
```

---

## 결정 11. DomainService 역할 — 도메인 내부 흐름 캡슐화

**결정**
- DomainService(Service 클래스)는 **도메인 내부 Entity 협력 흐름**을 담당한다.
- Facade는 **크로스 도메인 조율**만 담당한다.

**이유**
- A안(DomainService 없음)으로 가면 Facade가 `OrderItemModel`을 어떻게 만드는지,
  `OrderModel`이 어떤 인자를 받는지까지 알아야 한다.
  이는 도메인 지식이 Application Layer로 새는 것이다.
- 도메인 내부 흐름(어떻게 만드는가)은 Domain Layer에,
  도메인 간 조율(무엇을 조합하는가)은 Application Layer에 위치해야 한다.

**예시**

```java
// order/domain/OrderService.java — 도메인 내부 흐름 담당
public OrderModel createOrder(Long userId, List<OrderItemCommand> commands) {
    List<OrderItemModel> items = commands.stream()
        .map(c -> new OrderItemModel(c.productId(), c.productName(), c.price(), c.quantity()))
        .toList();
    OrderModel order = new OrderModel(userId, items);
    return orderRepository.save(order);
}

// order/application/OrderFacade.java — 크로스 도메인 조율만
public void createOrder(Long userId, List<OrderRequest> requests) {
    List<ProductModel> products = productService.findAllByIds(...);
    products.forEach(p -> p.decrementStock(qty));           // 재고 차감 (크로스 도메인)
    orderService.createOrder(userId, toCommands(products)); // 도메인 내부는 위임
}
```

**단, Service가 얇아지는 경우는 자연스러운 결과다**

`ProductService`처럼 크로스 도메인 협력이 없는 도메인은 Service가 Repository 위임 + NOT_FOUND 예외 수준으로 얇을 수 있다.
이건 잘못된 게 아니라, 도메인 내부 흐름이 단순하기 때문이다.

---

## 결정 12. 트랜잭션 경계

**결정**
- `@Transactional`은 **Facade에만** 위치한다.
- Service는 `@Transactional` 없이, Facade 트랜잭션 안에서 실행된다.

```java
// 쓰기 유스케이스
@Transactional
public void createOrder(...) {
    products.forEach(p -> p.decrementStock(qty));  // ─┐
    orderService.createOrder(userId, items);        // ─┘ 하나의 트랜잭션
}

// 읽기 유스케이스
@Transactional(readOnly = true)
public ProductInfo getProductDetail(Long id) { ... }
```

**이유**
- 트랜잭션은 유스케이스 단위로 묶여야 한다.
  `재고 차감 + 주문 저장`이 하나의 원자 단위여야 하고, 이를 조율하는 곳이 Facade다.
- Service는 항상 Facade를 통해 호출되므로, Facade의 트랜잭션에 참여한다.
  Spring 기본 전파 방식(`REQUIRED`)으로 중첩 트랜잭션 없이 동작한다.

---

## 결정 13. 예외 처리 전략

**결정 — 레이어별 예외 책임**

| 레이어 | 책임 | 예시 |
|--|--|--|
| **Model** | 비즈니스 규칙 위반 | `decrementStock` → 재고 부족 시 `BAD_REQUEST` |
| **Service** | 존재 여부 검증 | `findById` → 없으면 `NOT_FOUND` |
| **Facade** | 권한·크로스 도메인 검증 | 본인 브랜드 상품인지 확인 → `FORBIDDEN` |
| **Interfaces** | HTTP 요청 형식 검증 | `@Valid` DTO 검증 |

**예시**

```java
// Model 레벨 — 규칙 위반
public void decrementStock(int quantity) {
    if (this.stock < quantity) {
        throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
    }
    this.stock -= quantity;
}

// Service 레벨 — 존재 검증
public ProductModel findById(Long id) {
    return productRepository.findById(id)
        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));
}

// Facade 레벨 — 권한 검증
public void updateProduct(Long userId, Long productId, ...) {
    UserModel user = userService.findById(userId);
    ProductModel product = productService.findById(productId);
    if (!product.getBrand().getId().equals(user.getBrandId())) {
        throw new CoreException(ErrorType.FORBIDDEN, "담당 브랜드의 상품만 수정할 수 있습니다.");
    }
}
```

---

## 전체 패키지 스케치

```
com.loopers/
│
├── support/
│   ├── error/
│   │   ├── CoreException.java
│   │   └── ErrorType.java
│   ├── response/
│   │   ├── ApiResponse.java
│   │   └── ApiControllerAdvice.java
│   └── auth/
│       ├── CurrentUser.java
│       ├── LoginUser.java
│       └── LoginUserResolver.java
│
├── user/
│   ├── domain/
│   │   ├── UserModel.java
│   │   ├── UserRepository.java
│   │   ├── UserService.java
│   │   ├── Gender.java
│   │   └── PasswordEncryptor.java
│   ├── application/
│   │   ├── UserFacade.java
│   │   └── UserInfo.java
│   ├── infrastructure/
│   │   ├── UserRepositoryImpl.java
│   │   └── UserJpaRepository.java
│   └── interfaces/
│       ├── UserV1Controller.java
│       └── UserV1Dto.java
│
├── brand/
│   ├── domain/
│   │   ├── BrandModel.java
│   │   ├── BrandRepository.java
│   │   └── BrandService.java
│   ├── application/
│   │   ├── BrandFacade.java
│   │   └── BrandInfo.java
│   ├── infrastructure/
│   │   ├── BrandRepositoryImpl.java
│   │   └── BrandJpaRepository.java
│   └── interfaces/
│       ├── BrandV1Controller.java
│       ├── BrandV1Dto.java
│       ├── AdminBrandV1Controller.java
│       └── AdminBrandV1Dto.java
│
├── product/
│   ├── domain/
│   │   ├── ProductModel.java
│   │   ├── ProductRepository.java
│   │   ├── ProductService.java
│   │   └── SortCondition.java
│   ├── application/
│   │   ├── ProductFacade.java
│   │   └── ProductInfo.java
│   ├── infrastructure/
│   │   ├── ProductRepositoryImpl.java
│   │   └── ProductJpaRepository.java
│   └── interfaces/
│       ├── ProductV1Controller.java
│       ├── ProductV1Dto.java
│       ├── AdminProductV1Controller.java
│       └── AdminProductV1Dto.java
│
├── like/
│   ├── domain/
│   │   ├── LikeModel.java
│   │   ├── LikeRepository.java
│   │   └── LikeService.java
│   ├── application/
│   │   ├── LikeFacade.java
│   │   └── LikeInfo.java
│   ├── infrastructure/
│   │   ├── LikeRepositoryImpl.java
│   │   └── LikeJpaRepository.java
│   └── interfaces/
│       ├── LikeV1Controller.java
│       └── LikeV1Dto.java
│
└── order/
    ├── domain/
    │   ├── OrderModel.java
    │   ├── OrderItemModel.java
    │   ├── OrderStatus.java
    │   ├── OrderRepository.java
    │   └── OrderService.java
    ├── application/
    │   ├── OrderFacade.java
    │   └── OrderInfo.java
    ├── infrastructure/
    │   ├── OrderRepositoryImpl.java
    │   └── OrderJpaRepository.java
    └── interfaces/
        ├── OrderV1Controller.java
        ├── OrderV1Dto.java
        ├── AdminOrderV1Controller.java
        └── AdminOrderV1Dto.java
```
