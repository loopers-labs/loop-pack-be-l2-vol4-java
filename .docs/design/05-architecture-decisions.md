# 05. Architecture Decisions — 아키텍처 결정 기록

> 구현 과정에서 내린 아키텍처·설계 결정과 그 이유를 기록합니다.
> "왜 이렇게 했지?"를 나중에 떠올릴 때 참조하세요.

---

## 결정 1. 4티어 레이어드 아키텍처

**결정**
```
interfaces → application → domain ← infrastructure
```

**레이어별 역할**

| 레이어 | 책임 |
|--------|------|
| interfaces | HTTP 요청/응답, DTO 변환, 요청 형식 검증 |
| application (Facade) | Repository 로드·저장 + DomainService 호출 순서 조율 |
| domain (Model / VO / Specification / Policy / Service) | 순수 비즈니스 로직 |
| infrastructure | Repository 구현체, JPA |

**핵심 원칙**
- Facade는 비즈니스 로직을 인라인으로 직접 작성하지 않는다.
- 비즈니스 로직은 반드시 Domain Layer에 위치한다.

**이유**
- 헥사고날/클린 아키텍처는 Port/Adapter 개념과 추가 파일로 복잡도가 높다.
- 인프라 교체 가능성이 낮은 규모에서 불필요한 추상화다.
- 레이어 간 단방향 의존으로 변경 영향 범위를 제한하는 것만으로 충분하다.

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
└── support/            ← 도메인 무관 공통 코드 (Specification 인터페이스 포함)
```

**이유**
- 한 도메인의 관련 코드가 한 패키지 안에 모여 파편화가 줄어든다.
- 레이어 서브패키지를 유지하면 import 경로만 봐도 레이어 침범이 눈에 띈다.

**약하게 선택한 이유**
- 단일 Gradle 모듈이라 빌드 시스템이 레이어 경계를 강제하지 않는다.
- 레이어 서브패키지를 포기하면 경계를 개발자 규율에만 의존해야 한다.

---

## 결정 3. DomainService — 순수 도메인 로직

**결정**
- DomainService는 Repository 의존 없이 순수 도메인 로직만 담당한다.
- Facade가 로드한 도메인 객체를 파라미터로 받아 비즈니스 규칙을 수행하고, 도메인 객체를 반환한다.

**이유**
- Repository를 주입받으면 도메인 로직이 인프라 의존성을 갖게 된다.
- Repository를 분리하면 순수 Java 단위 테스트로 비즈니스 로직을 검증할 수 있다.
- 도메인 로직(어떻게 처리하는가)은 Domain에, 로드·저장(어디서 가져오는가)은 Facade에 위치한다.

**역할 분담**

| 클래스 | 책임 |
|--------|------|
| DomainService | 순수 비즈니스 규칙 — 엔티티를 파라미터로 받아 검증·변환·조립·상태 변경 |
| Facade | Repository 로드·저장 + DomainService 호출 순서 조율 |

**예시 — 크로스 도메인 협력 (Brand 삭제)**

```java
// brand/domain/BrandService.java — Repository 없음, 크로스 도메인 비즈니스 로직
public void deleteCascade(BrandModel brand, List<ProductModel> products) {
    products.forEach(ProductModel::delete);
    brand.delete();
}

// brand/application/BrandFacade.java — 로드·저장·조율만
@Transactional
public void deleteBrand(Long brandId) {
    BrandModel brand = brandService.getOrThrow(brandRepository.find(brandId));
    List<ProductModel> products = productRepository.findAllByBrandId(brandId);
    brandService.deleteCascade(brand, products);
    products.forEach(productRepository::save);
    brandRepository.save(brand);
}
```

**단순 조회의 경우 — getOrThrow**

"존재하지 않으면 NOT_FOUND 예외"는 도메인 규칙이므로 DomainService가 담당한다.
Facade는 Repository 결과를 DomainService에 넘기고, DomainService가 존재 여부를 판단한다.

```java
// product/domain/ProductService.java
public ProductModel getOrThrow(Optional<ProductModel> product) {
    return product.orElseThrow(() -> new CoreException(NOT_FOUND, "상품이 존재하지 않습니다."));
}

// product/application/ProductFacade.java
public ProductInfo getProduct(Long id) {
    Optional<ProductModel> product = productRepository.find(id);
    return ProductInfo.from(productService.getOrThrow(product));
}
```

---

## 결정 4. 트랜잭션 경계

**결정**
- `@Transactional`은 Facade에만 위치한다.
- DomainService는 `@Transactional` 없이 Facade 트랜잭션에 참여한다.

```java
@Transactional
public OrderInfo createOrder(...) { ... }          // 쓰기

@Transactional(readOnly = true)
public ProductInfo getProduct(Long id) { ... }     // 읽기
```

**이유**
- 트랜잭션은 유스케이스 단위로 묶여야 한다.
  예를 들어 `재고 선점 + 저장`(pay/start), `재고 확정 + 주문 상태 변경`(pay/confirm)이 각각 하나의 원자 단위이고, 이를 조율하는 곳이 Facade다.
- Spring 기본 전파 방식(`REQUIRED`)으로 DomainService는 Facade 트랜잭션에 자연스럽게 참여한다.

---

## 결정 5. Value Object — Price

**결정**
- `Price`(가격)를 `product/domain/`에 Java `record`로 분리한다.
- JPA Entity 필드(`Long price`)는 그대로 유지한다.
  VO는 검증 경유 목적으로만 사용하고 `.value()`로 primitive를 꺼내 Entity에 저장한다.

**도입 이유**

VO 도입 전, 가격 검증 로직이 생성자·`update()`·`OrderItemModel` 등 여러 곳에 분산되어 있었다.

**설계**

```java
// product/domain/Price.java
public record Price(Long value) {
    public Price {
        if (value == null) throw new CoreException(BAD_REQUEST, "가격은 비어있을 수 없습니다.");
        if (value < 0)    throw new CoreException(BAD_REQUEST, "가격은 0 이상이어야 합니다.");
    }
}
```

**사용 패턴**

```java
// ProductModel 생성자·update — VO 경유 검증 후 primitive 저장
this.price = new Price(price).value();
```

**효과**

| | Before | After |
|---|---|---|
| 가격 검증 | 생성자·`update`·OrderItemModel 3곳 중복 | `Price` 1곳 |
| 검증 규칙 변경 시 | 여러 파일 수정 필요 | `Price` 1곳만 수정 |

**테스트 책임 분리**

| 테스트 파일 | 담당 |
|---|---|
| `PriceTest` | 가격 null·음수·0·양수 생성 케이스 |
| `ProductModelTest` | 상품명·설명 검증 (ProductModel 고유 책임) + 정상 생성·수정 확인 |

---

## 결정 6. Specification 패턴 — 조건 캡슐화

**결정**
- 순수 boolean 판단 조건은 `Specification<T>` 인터페이스로 캡슐화한다.
- 존재 여부 + 엔티티 추출이 함께 필요한 경우는 `getOrThrow()` 패턴을 유지한다 (대체 시 호출부가 복잡해짐).

**인터페이스**

```java
// support/Specification.java
public interface Specification<T> {
    boolean isSatisfiedBy(T candidate);
}
```

**적용 기준**

| 조건 유형 | 처리 방식 |
|---|---|
| 엔티티 추출이 필요한 존재 확인 | `getOrThrow(Optional<T>)` 유지 |
| 순수 boolean 판단 | `Specification<T>` 도입 |

**적용된 Specification**

| 클래스 | 위치 | 조건 |
|---|---|---|
| `ProductExistsSpecification` | `product/domain/` | 상품이 존재해야 함 |
| `LikeNotDuplicateSpecification` | `like/domain/` | 좋아요가 중복되지 않아야 함 |
| `OrderOwnerSpecification` | `order/domain/` | 주문의 userId와 요청 userId가 일치해야 함 |

**`OrderOwnerSpecification` — userId 생성자 주입**

```java
public class OrderOwnerSpecification implements Specification<OrderModel> {
    private final Long userId;

    public OrderOwnerSpecification(Long userId) { this.userId = userId; }

    @Override
    public boolean isSatisfiedBy(OrderModel order) {
        return order.getUserId().equals(userId);
    }
}
```

**부수 효과 — Facade 인라인 비즈니스 로직 제거**

`OrderOwnerSpecification` 도입으로 `OrderFacade`의 소유권 체크 인라인이
`OrderService.checkOwnership()`(DomainService)으로 이동했다. 결정 1의 원칙과 일치한다.

```java
// Before — Facade에 비즈니스 로직 인라인
if (!order.getUserId().equals(userId)) throw new CoreException(FORBIDDEN, ...);

// After — DomainService에 위임
orderService.checkOwnership(order, userId);
```

---

## 결정 7. Policy 패턴 — 정책 캡슐화

**결정**
- 여러 Specification이 함께 충족되어야 하는 비즈니스 정책은 Policy 클래스로 캡슐화한다.
- Policy는 관련 Specification들을 순서 있게 조합하여 "이 행위가 가능한가?"를 하나의 단위로 표현한다.

**적용 기준**
- 두 개 이상의 조건이 함께 충족되어야 하나의 행위가 가능한 경우
- 조건들이 여러 레이어(Facade, Service)에 분산되어 있어 정책 단위가 불명확한 경우

**적용된 Policy**

```java
// like/domain/LikeRegistrationPolicy.java
@Component
public class LikeRegistrationPolicy {
    private final ProductExistsSpecification productExists = new ProductExistsSpecification();
    private final LikeNotDuplicateSpecification notDuplicate = new LikeNotDuplicateSpecification();

    public void check(Optional<ProductModel> product, Optional<LikeModel> existing) {
        if (!productExists.isSatisfiedBy(product)) {
            throw new CoreException(NOT_FOUND, "상품이 존재하지 않습니다.");
        }
        if (!notDuplicate.isSatisfiedBy(existing)) {
            throw new CoreException(CONFLICT, "이미 좋아요한 상품입니다.");
        }
    }
}
```

**호출 흐름**

```
Before:
  LikeFacade  → productService.getOrThrow()           // 상품 존재 확인 (Facade 직접 검증)
  LikeService → if (existing.isPresent()) throw ...   // 중복 확인 (Service 인라인)

After:
  LikeFacade  → likeRegistrationPolicy.check(product, existing)
                   ├─ ProductExistsSpecification      // 상품 존재 확인
                   └─ LikeNotDuplicateSpecification   // 중복 확인
  LikeService → createLike(userId, productId)         // 순수 생성만 담당
```

**효과**
- "좋아요 등록 가능 여부"라는 비즈니스 정책이 `LikeRegistrationPolicy` 한 곳에 명시적으로 표현된다.
- `LikeService.createLike()`가 순수 객체 생성 책임만 갖게 된다.
- Policy 단위의 단독 테스트가 가능해진다.

---

## 결정 8. DIP 적용 범위

**결정**
- 적용: `Repository` 인터페이스, `PasswordEncryptor` 인터페이스
- 미적용: `Facade`, `DomainService`, `Specification`, `Policy`

**이유**
- Repository는 InMemory Fake 또는 Mock으로 교체 가능성이 있다.
- PasswordEncryptor는 테스트에서 NoOp 구현체로 교체하기 위해 인터페이스가 필요하다.
- Facade, DomainService, Specification, Policy는 교체할 시나리오가 없으므로 구체 클래스를 직접 사용한다.

---

## 결정 9. 도메인 간 의존 원칙

**확정된 원칙**

| 레이어 | 허용 | 금지 |
|--------|------|------|
| Domain | 파라미터로 전달받은 타 도메인 Model 사용 | 타 도메인 Service / Repository 직접 참조 |
| Application | Repository 로드·저장, DomainService 호출 | 크로스 도메인 비즈니스 로직 인라인 |

**ProductModel — 결합 제거 결정 (`Long brandId`)**

`ProductModel`은 `BrandModel`을 `@ManyToOne`으로 참조하는 대신 `Long brandId`(nullable)만 저장한다.

```java
class ProductModel {
    private Long brandId;  // nullable — 브랜드 없는 상품 허용
}
```

이유: `product/domain`이 `brand/domain`을 전혀 모르는 단방향 의존만 남긴다.
브랜드 정보가 필요한 경우 Facade에서 IN 쿼리로 일괄 조회 후 조합한다.

**전체 의존 방향 지도**

```
[Domain — Service/Repository 수평 의존 없음, 파라미터로 타 도메인 Model 수신 가능]
product/domain  (brand/domain을 import하지 않음)
stock/domain    (product/domain을 import하지 않음)
order/domain    ← (파라미터로) ProductModel 수신
brand/domain    ← (파라미터로) ProductModel 수신
like/domain     ← (파라미터로) ProductModel 수신 (Policy의 Specification 경유)

[Application — 로드·저장·순서 조율만]
product/application ──► brand/domain    (브랜드 정보 로드 후 ProductService에 위임)
                    ──► stock/domain    (상품 조회 시 가용 재고 포함)
like/application    ──► product/domain  (상품 로드 후 LikeRegistrationPolicy에 위임)
                    ──► stock/domain    (좋아요 목록 조회 시 가용 재고 포함)
order/application   ──► product/domain  (상품 로드 후 OrderService에 위임)
                    ──► stock/domain    (결제 진입·완료 시 재고 선점·확정)
brand/application   ──► product/domain  (상품 로드 후 BrandService에 위임)
```

---

## 결정 10. 예외 처리 전략

**레이어별 예외 책임**

| 레이어 | 책임 | 예시 |
|--------|------|------|
| **Model / VO** | 비즈니스 규칙 위반 | `Stock.decrease()` → 재고 부족 시 `BAD_REQUEST` |
| **Specification** | 단일 조건 판단 — boolean 반환, 예외 미발생 | `ProductExistsSpecification.isSatisfiedBy()` |
| **Policy** | 정책 단위 검증 → 위반 시 예외 | `LikeRegistrationPolicy.check()` → `NOT_FOUND` / `CONFLICT` |
| **Service** | 존재 여부·소유권 검증 | `getOrThrow()` → `NOT_FOUND`; `checkOwnership()` → `FORBIDDEN` |
| **Facade** | DB 조회가 필요한 크로스 도메인 검증 | 상품 수량 불일치 → `NOT_FOUND` |
| **Interfaces** | HTTP 요청 형식, DB 조회 없이 가능한 권한 확인 | `@Valid`; 경로 변수 userId 불일치 → `FORBIDDEN` |

---

## 결정 11. likeCount 업데이트 — 원자 쿼리

**결정**
- Repository의 `@Modifying @Query`로 원자적으로 처리한다.

**이유**
- 동시 좋아요 요청 시 Java 필드 수정 + JPA dirty checking은 Lost Update가 발생한다.
- `UPDATE SET like_count = like_count + 1`은 DB가 원자적으로 처리해 동시성이 안전하다.

**주의**
- 좋아요 등록·취소 후 `productRepository.incrementLikeCount()` / `decrementLikeCount()`를
  LikeFacade에서 반드시 명시적으로 호출해야 한다.

---

## 결정 12. 기타 결정

**Admin/Customer API 분리**
- 같은 `interfaces/` 안에서 파일명으로 구분한다.
  - 고객: `BrandV1Controller.java` / 어드민: `AdminBrandV1Controller.java`

**OrderItemModel 위치**
- `order/domain/` 안에 포함한다. OrderModel 없이 독립 존재 불가이므로 분리하지 않는다.

**본인 소유 리소스 접근 — FORBIDDEN**

| 엔드포인트 | 처리 위치 | 이유 |
|---|---|---|
| `GET /api/v1/orders/{orderId}` | `OrderService.checkOwnership()` | DB 조회 후 비교 → DomainService |
| `GET /api/v1/users/{userId}/likes` | `LikeV1Controller` | 경로 변수만으로 판단 가능 → Interfaces |

**N+1 방지 — IN 쿼리 일괄 조회**
- 좋아요 목록·상품 목록 조회 시 ID 목록으로 `findAllByIds()` IN 쿼리를 사용한다.

---

## 결정 13. Stock — 독립 도메인 분리

**결정**
- `stock/`을 `product/`와 독립된 별도 도메인으로 분리한다.
- `ProductModel`에서 `stock` 필드를 제거하고, `StockModel`이 재고를 단독으로 관리한다.
- `stocks` 테이블은 `product_id`(FK), `total_stock`, `reserved_stock`을 갖는다.

**배경**
- 결제 플로우 도입(주문 생성 → 결제 창 선점 → 결제 완료 확정)으로 재고가 `선점(reserved)` / `확정(confirmed)` / `해제(released)` 상태를 갖게 됨
- 재고 변경과 상품 정보 변경의 이유가 다름: 상품명·가격 수정과 재고 수량 조정은 서로 다른 유스케이스에서 발생
- 향후 재고 이력 추적(`stock_histories`) 추가 시 `product/`를 건드리지 않아도 됨

**트레이드오프**

| 항목 | 이점 | 비용 |
|------|------|------|
| 변경 이유 분리 | 상품 정보와 재고가 각자 이유로 변경 가능 | 상품 조회 시 재고를 함께 보여주려면 join 또는 별도 조회 필요 |
| 선점 플로우 표현 | `reserved_stock` 상태를 Stock 자체가 관리 | `StockFacade` 추가, `OrderFacade`에서 cross-domain 호출 증가 |
| 도메인 경계 명확 | Stock이 자체 Repository·Service를 가짐 | 전체 패키지 구조 변경, `ProductModel.decreaseStock()` 제거 |
| 동시성 처리 집중 | 낙관적/비관적 잠금을 Stock 도메인 한 곳에서 처리 | 선점 만료 처리를 위한 스케줄러 별도 구현 필요 |

**결제 플로우와의 연결**

```
POST /orders                     → OrderStatus: PENDING_PAYMENT  (재고 변동 없음)
POST /orders/{id}/pay/start      → StockModel.reserve(quantity)  (reserved_stock += quantity)
POST /orders/{id}/pay/confirm    → StockModel.confirm(quantity)  (total_stock -= quantity, reserved_stock -= quantity)
                                 → OrderStatus: CONFIRMED
```

**도메인 간 의존 방향**
- `order/application` → `stock/domain` (StockRepository 로드·저장)
- `stock/domain`은 `order/domain`을 알지 못함
- 가용 재고 계산: `availableStock = total_stock - reserved_stock` (StockModel 내부 메서드)

---

## 전체 패키지 스케치

```
com.loopers/
│
├── support/
│   ├── Specification.java              ← 제네릭 인터페이스
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
│   ├── application/  (UserFacade, UserInfo)
│   ├── infrastructure/  (UserRepositoryImpl, UserJpaRepository)
│   └── interfaces/  (UserV1Controller, UserV1Dto)
│
├── brand/
│   ├── domain/
│   │   ├── BrandModel.java
│   │   ├── BrandRepository.java
│   │   └── BrandService.java
│   ├── application/  (BrandFacade, BrandInfo)
│   ├── infrastructure/  (BrandRepositoryImpl, BrandJpaRepository)
│   └── interfaces/  (BrandV1Controller, AdminBrandV1Controller, ...)
│
├── product/
│   ├── domain/
│   │   ├── ProductModel.java
│   │   ├── ProductRepository.java
│   │   ├── ProductService.java
│   │   ├── SortCondition.java
│   │   ├── Price.java                          ← VO
│   │   └── ProductExistsSpecification.java     ← Specification
│   ├── application/  (ProductFacade, ProductInfo)
│   ├── infrastructure/  (ProductRepositoryImpl, ProductJpaRepository)
│   └── interfaces/  (ProductV1Controller, AdminProductV1Controller, ...)
│
├── stock/                                      ← 결정 13: 독립 도메인
│   ├── domain/
│   │   ├── StockModel.java                     (productId, totalStock, reservedStock + reserve/confirm/release)
│   │   ├── StockRepository.java
│   │   └── StockService.java                   (getOrThrow)
│   ├── application/  (StockFacade, StockInfo)
│   └── infrastructure/  (StockRepositoryImpl, StockJpaRepository)
│
├── like/
│   ├── domain/
│   │   ├── LikeModel.java
│   │   ├── LikeRepository.java
│   │   ├── LikeService.java
│   │   ├── LikeNotDuplicateSpecification.java  ← Specification
│   │   └── LikeRegistrationPolicy.java         ← Policy
│   ├── application/  (LikeFacade, LikeInfo)
│   ├── infrastructure/  (LikeRepositoryImpl, LikeJpaRepository)
│   └── interfaces/  (LikeV1Controller, LikeV1Dto)
│
└── order/
    ├── domain/
    │   ├── OrderModel.java
    │   ├── OrderItemModel.java
    │   ├── OrderStatus.java
    │   ├── OrderRepository.java
    │   ├── OrderService.java
    │   └── OrderOwnerSpecification.java         ← Specification
    ├── application/  (OrderFacade, OrderInfo, OrderItemCommand, OrderItemInfo)
    ├── infrastructure/  (OrderRepositoryImpl, OrderJpaRepository)
    └── interfaces/  (OrderV1Controller, OrderV1Dto)
```
