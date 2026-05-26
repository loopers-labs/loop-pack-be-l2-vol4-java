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
- Application Layer(Facade)는 Repository 로드·저장과 도메인 간 조율을 담당한다.
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
- Repository는 Facade 단위 테스트에서 Mock으로 교체할 수 있어야 한다.
  (결정 11에 따라 Facade가 Repository를 직접 호출하므로, Facade 테스트 시 Repository Mock이 필요하다.)
- PasswordEncryptor는 DomainService에서 사용하며, 테스트에서 NoOp 구현체로 교체하기 위해 인터페이스가 필요하다.
  Spring Security의 `PasswordEncoder`를 직접 주입하지 않고, 반드시 `PasswordEncryptor` 인터페이스를 통해 사용한다.
  인터페이스는 `encrypt(rawPassword): String`과 `matches(rawPassword, encodedPassword): boolean` 두 메서드를 포함한다.
  (`matches`는 changePassword 시 "현재 비밀번호와 동일 불가" 검증에 필요하다.)
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
    // Facade가 Repository를 직접 호출해 로드
    BrandModel brand = brandService.getOrThrow(brandRepository.findById(brandId));
    List<ProductModel> products = productRepository.findAllByBrandId(brandId);
    List<UserModel> brandAdmins = userRepository.findAllByBrandId(brandId);

    // 각 DomainService에 도메인 로직 위임
    products.forEach(productService::softDelete);       // 상품 소프트 딜리트
    brandAdmins.forEach(userService::softDelete);       // BRAND_ADMIN 소프트 딜리트
    brandService.softDelete(brand);                     // 브랜드 소프트 딜리트
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

**⚠️ 구현 선행 조건**
- `BaseEntity` / `SoftDeletableEntity` 분리는 **모든 Model 구현 전에** 완료해야 한다.
- 이후에 수정하면 Entity 전체를 다시 손봐야 하므로, 가장 먼저 처리한다.

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

**결정: 결합 제거 → Long brandId (nullable)**

브랜드는 선택 사항이므로 `@ManyToOne BrandModel` 대신 `Long brandId`(nullable)를 사용한다.

- 브랜드 없이도 상품을 등록할 수 있다 (노브랜드 상품).
- `@ManyToOne`으로 nullable 관계를 표현하면 product/domain이 brand/domain을 import하게 된다.
  브랜드가 항상 존재한다는 보장이 없으므로, JOIN FETCH 단순화 이점도 사라진다.
- `Long brandId`를 저장하고, 상품 목록 조회 시 Facade에서 brandId 목록으로 브랜드 정보를 일괄 조회해 조합한다.
- 도메인 완전 독립을 통해 product/domain이 brand/domain을 전혀 모르는 단방향 의존만 남는다.

```java
class ProductModel {
    private Long brandId;  // nullable — 브랜드 없는 상품 허용
}

// ProductFacade에서 IN 쿼리로 브랜드 일괄 조회 후 조합
List<Long> brandIds = products.stream()
    .map(ProductModel::getBrandId)
    .filter(Objects::nonNull)
    .distinct()
    .toList();
Map<Long, BrandModel> brandMap = brandRepository.findAllByIds(brandIds)
    .stream().collect(toMap(BrandModel::getId, identity()));
```

**확정된 원칙**
- Domain 레이어: 자기 도메인 로직만 담당. 다른 도메인의 Service/Repository 직접 참조 금지.
- Application 레이어: 도메인 간 협력이 일어나는 유일한 곳. 브랜드·상품 정보 조합도 Facade에서 처리.
- `Application → Domain ← Infrastructure` 레이어 원칙 준수.

**전체 의존 방향 지도**

```
[Domain 레이어 — 수평 의존 없음]
product/domain  (brand/domain을 import하지 않음)

[Application 레이어 — 도메인 간 조합]
product/application ──► brand/domain  (상품 조회 시 브랜드 정보 조합)
like/application    ──► product/domain  (likeCount 업데이트, 좋아요 목록 상품 조회)
order/application   ──► product/domain  (재고 차감, 스냅샷)
brand/application   ──► product/domain  (브랜드 삭제 연쇄)
brand/application   ──► user/domain     (브랜드 삭제 연쇄)
```

**유사 관계 검토 — User(BRAND_ADMIN) → Brand**

Product → Brand와 동일한 패턴이 User → Brand에도 존재한다.
BRAND_ADMIN은 브랜드에 귀속되고, 브랜드 삭제 시 함께 소프트 딜리트된다.
두 경우 모두 `Long brandId`를 저장하는 방식으로 통일된다.

| | Product → Brand | User → Brand |
|--|--|--|
| 관계 | 브랜드 없이 등록 가능, 브랜드 지정 시 해당 브랜드 소속 | BRAND_ADMIN은 브랜드에 귀속 |
| 브랜드 삭제 시 | 소속 상품 소프트 딜리트 | BRAND_ADMIN 소프트 딜리트 |
| 구현 | `Long brandId` (nullable) | `Long brandId` (nullable) |

두 경우 모두 `Long brandId`만 저장한다. 브랜드 이름 표시가 필요한 경우 Facade에서 조합한다.

---

## 결정 10. 내 좋아요 목록 조회 — 상품 정보 조회 방식

**상황**
`LikeModel`은 `productId(Long)`만 갖는다.
좋아요 목록 조회 시 각 상품 정보가 필요하므로 `like/application → product/domain` 의존이 생긴다.

**선택지**

- A안: productId마다 `productRepository.findById()`를 호출 → N+1 발생 (페이지당 최대 N번 쿼리)
- B안: productId 목록으로 `productRepository.findAllByIds()` IN 쿼리 → 페이지당 쿼리 2번으로 고정

**결정: B안 (IN 쿼리)**

오프셋 페이지네이션으로 한 페이지가 고정되더라도 페이지 요청마다 N번 vs 2번 차이가 난다.
`productRepository.findAllByIds(List<Long>)` 메서드 하나 추가로 해결되므로 B안을 선택한다.
Facade가 Repository를 직접 호출하므로 DomainService를 거치지 않는다.

```java
// like/application/LikeFacade.java
List<LikeModel> likes = likeRepository.findByUserId(userId, pageable); // Facade가 직접 로드

List<Long> productIds = likes.stream()
    .map(LikeModel::getProductId)
    .toList();

List<ProductModel> products = productRepository.findAllByIds(productIds); // Facade가 직접 로드
```

---

## 결정 11. DomainService 역할 — 순수 도메인 로직 캡슐화

**결정**
- DomainService(Service 클래스)는 **Repository 의존 없이** 순수 도메인 로직만 담당한다.
- DomainService 메서드는 도메인 객체를 파라미터로 받아 비즈니스 규칙을 수행하고, 도메인 객체를 반환한다.
- Facade가 Repository 로드·저장과 크로스 도메인 조율을 모두 담당한다.

**고민한 배경 — 두 가지 선택지**

DomainService가 Repository를 직접 쥐는 방식과 순수 도메인 로직만 담는 방식 사이에서 선택이 필요했다.

**A안: DomainService → Repository 의존 (기존 방식)**

```java
// UserService.java
public UserModel signUp(UserModel user) {
    userRepository.findByLoginId(user.getLoginId())  // 로드
        .ifPresent(e -> { throw new CoreException(...); });
    user.encodePassword(encryptor);
    return userRepository.save(user);                // 저장
}
```

| 장점 | 단점 |
|------|------|
| 로드·로직·저장이 한 메서드에 완결 — 흐름이 단순 | DomainService가 인프라(Repository)에 의존 |
| Facade가 얇아짐 | DomainService 단독 단위 테스트 시 Repository Mock 필요 |
| 직관적 (`signUp()` 하나로 완결) | 도메인 로직과 데이터 접근 책임이 혼재 |

**B안: 순수 DDD DomainService (선택)**

```java
// UserService.java — Repository 없음
public UserModel signUp(Optional<UserModel> existing, UserModel newUser) {
    existing.ifPresent(e -> { throw new CoreException(...); }); // 순수 로직만
    newUser.encodePassword(encryptor);
    return newUser;
}

// UserFacade.java — 로드·저장 담당
@Transactional
public UserInfo signUp(...) {
    Optional<UserModel> existing = userRepository.findByLoginId(loginId);
    UserModel user = userService.signUp(existing, new UserModel(...));
    userRepository.save(user);
    return UserInfo.from(user);
}
```

| 장점 | 단점 |
|------|------|
| DomainService가 인프라 무관 — 순수 Java | Facade가 두꺼워짐 (로드·저장·조율 모두 담당) |
| DomainService는 엔티티만 받으면 되므로 단독 테스트 용이 | 단순 CRUD에도 Facade·Service를 나눠야 함 |
| 로직(DomainService)과 흐름 제어(Facade) 책임이 명확 | 파라미터가 복잡해질 수 있음 |

**결정: B안 — 순수 DDD DomainService**

- 과제 목표가 DDD 패턴을 직접 경험하는 것이므로, 실용성보다 원칙을 따른다.
- 두 방식을 혼용(하이브리드)하면 도메인마다 결정 기준이 달라져 코드베이스 일관성이 깨진다.
- 통일된 패턴이 학습 목적에서도, 유지보수 측면에서도 낫다.

**이유**
- DomainService가 Repository를 주입받으면 도메인 로직이 인프라 의존성을 갖게 된다.
- Repository를 분리하면 DomainService는 순수 Java 객체로 유지되어 테스트와 재사용이 쉬워진다.
- 도메인 로직(어떻게 처리하는가)은 Domain Layer에,
  로드·저장(어디서 가져오고 저장하는가)은 Application Layer에 위치해야 한다.

**역할 분담**

| 레이어 | 책임 |
|--------|------|
| DomainService | 순수 비즈니스 규칙 — 엔티티를 받아 검증·변환·조립 |
| Facade | Repository 로드·저장 + 크로스 도메인 조율 |

**예시**

```java
// order/domain/OrderService.java — Repository 없음, 순수 도메인 로직만
public OrderModel createOrder(Long userId, List<OrderItemCommand> commands) {
    List<OrderItemModel> items = commands.stream()
        .map(c -> new OrderItemModel(c.productId(), c.productName(), c.price(), c.quantity()))
        .toList();
    return new OrderModel(userId, items);  // 저장은 Facade가
}

// order/application/OrderFacade.java — 로드·저장·크로스 도메인 조율
@Transactional
public void createOrder(Long userId, List<OrderRequest> requests) {
    List<ProductModel> products = productRepository.findAllByIds(...); // Facade가 로드
    products.forEach(p -> p.decrementStock(qty));                      // 크로스 도메인 로직
    OrderModel order = orderService.createOrder(userId, toCommands(products)); // 도메인 로직 위임
    orderRepository.save(order);                                       // Facade가 저장
}
```

**단순 조회의 경우 — finder 역할도 DomainService가 담당**

단순 조회(`findById`)처럼 도메인 로직이 없어 보이는 경우도,
"존재하지 않으면 NOT_FOUND 예외"는 도메인 규칙이므로 DomainService가 담당한다.
Facade는 Repository 결과를 DomainService에 넘기고, DomainService가 존재 여부를 판단한다.

```java
// product/domain/ProductService.java
public ProductModel getOrThrow(Optional<ProductModel> product) {
    return product.orElseThrow(() -> new CoreException(NOT_FOUND, "상품이 존재하지 않습니다."));
}

// product/application/ProductFacade.java
@Transactional(readOnly = true)
public ProductInfo getProduct(Long id) {
    Optional<ProductModel> product = productRepository.find(id); // Facade가 로드
    return ProductInfo.from(productService.getOrThrow(product)); // 존재 검증은 DomainService
}
```

---

## 결정 12. 트랜잭션 경계

**결정**
- `@Transactional`은 **Facade에만** 위치한다.
- Service는 `@Transactional` 없이, Facade 트랜잭션 안에서 실행된다.

```java
// 쓰기 유스케이스 — Facade가 로드·저장·트랜잭션 경계 모두 담당
@Transactional
public void createOrder(...) {
    List<ProductModel> products = productRepository.findAllByIds(...); // ─┐
    products.forEach(p -> p.decrementStock(qty));                      //  │ 하나의
    OrderModel order = orderService.createOrder(userId, items);        //  │ 트랜잭션
    orderRepository.save(order);                                       // ─┘
}

// 읽기 유스케이스
@Transactional(readOnly = true)
public ProductInfo getProductDetail(Long id) {
    Optional<ProductModel> product = productRepository.find(id);
    return ProductInfo.from(productService.getOrThrow(product));
}
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
| **Service** | 존재 여부 검증 | `getOrThrow(Optional)` → 없으면 `NOT_FOUND` |
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

// Service 레벨 — 존재 검증 (Repository 결과를 받아 판단, Repository 직접 호출 X)
public ProductModel getOrThrow(Optional<ProductModel> product) {
    return product.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));
}

// Facade 레벨 — 로드 + 권한 검증 (Repository는 Facade가 직접 호출)
public void updateProduct(String loginId, Long productId, ...) {
    UserModel user = userService.getOrThrow(userRepository.findByLoginId(loginId));
    ProductModel product = productService.getOrThrow(productRepository.find(productId));
    if (!Objects.equals(product.getBrandId(), user.getBrandId())) {
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
