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

**세 아키텍처 비교**

세 아키텍처는 모두 "비즈니스 로직이 인프라를 몰라야 한다"는 같은 문제를 풀지만, DIP를 어디까지 적용하느냐가 다르다.

| 아키텍처 | DIP 적용 범위 | 핵심 메커니즘 |
|----------|--------------|--------------|
| 레이어드 | 교체 가능성이 있는 곳만 (Repository, PasswordEncryptor) | 단방향 의존 + 선택적 DIP |
| 헥사고날 | Core의 모든 출입구 | Primary/Secondary Port 인터페이스 |
| 클린 | 모든 레이어 경계 | 의존성이 항상 안쪽을 향해야 한다는 절대 규칙 |

헥사고날과 클린은 레이어드를 원칙적으로 완전히 적용한 버전이다. 모든 경계에 Port(인터페이스)를 두면 인프라를 완전히 교체할 수 있지만, 파일 수와 추상화 비용이 매 커밋마다 발생한다.

**비즈니스 로직을 인프라에서 분리하는 이유**

"인프라가 자주 바뀌기 때문"이 아니다. 실제로는 반대다.
- 비즈니스 로직: 요구사항이 계속 바뀌므로 **자주** 바뀐다
- 인프라(DB): 교체하는 일이 드물므로 **느리게** 바뀐다

분리의 실질적인 이유는 세 가지다.

1. **변경의 이유가 다르다** — 비즈니스 로직은 요구사항 때문에 바뀌고, 인프라는 기술적 결정 때문에 바뀐다. DB가 안 바뀌더라도 비즈니스 로직은 계속 바뀐다. 자주 바뀌는 코드 안에 SQL이 섞여 있으면 비즈니스 규칙을 수정할 때마다 SQL도 같이 읽어야 하고, 실수로 건드릴 위험도 생긴다.

2. **테스트가 어려워진다** — 비즈니스 로직이 DB에 의존하면 "재고 부족 시 예외가 발생하는가?"를 테스트하려고 DB 연결, 데이터 세팅, rollback이 필요해진다. 분리하면 객체 하나 만들고 메서드 호출하는 것으로 끝난다. `StockModelTest`가 `@SpringBootTest` 없이 동작하는 이유가 이것이다.

3. **비즈니스 의도가 보이지 않는다** — SQL은 "어떻게 저장하는지"를 말하고, `stock.reserve(quantity)`는 "무슨 비즈니스 행위인지"를 말한다. 섞이면 코드를 읽는 사람이 핵심을 파악하는 데 시간이 더 걸린다.

실질적인 설득력 순서는 **테스트 > 가독성 > 변경의 이유 분리** 다.

**레이어드를 선택한 이유**

- 이 프로젝트에서 인프라(MySQL, JPA, Spring)는 교체할 일이 없다. 헥사고날/클린이 주는 "인프라 완전 교체 가능성"이라는 이점은 이 규모에서 실현될 가능성이 낮다.
- DomainService가 Repository를 갖지 않으므로 단위 테스트 격리는 이미 확보된다. 모든 경계에 Port를 두지 않아도 테스트 목표는 달성된다.
- 헥사고날/클린의 비용(Port 파일 추가, 추상화 레이어)은 매 커밋마다 발생하지만, 그 이익은 인프라를 교체할 때만 발생한다.

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

**대안: 레이어 중심 (Layer-first)**

레이어를 최상위에 두는 방식으로, Spring Boot 입문 튜토리얼에서 흔히 보이는 구조다.

```
com.loopers/
├── controller/   ← UserController, ProductController, OrderController ...
├── service/      ← UserService, ProductService, OrderService ...
├── repository/   ← UserRepository, ProductRepository ...
└── domain/       ← UserModel, ProductModel ...
```

**레이어 중심의 문제 — 응집도가 낮다**

"좋아요 기능을 전부 보고 싶다"고 하면 `controller/`, `service/`, `repository/`, `domain/` 네 폴더를 오가야 한다. 기능 하나가 여러 패키지에 파편화되어 있어, 수정할 때 컨텍스트를 잃기 쉽다.

도메인 중심이면 `like/` 폴더 하나만 열면 관련 코드가 전부 모여있다.

**패키지 이름이 시스템의 정체성을 말해야 한다**

레이어 중심 패키지 목록(`controller / service / repository / domain`)을 보면 이 시스템이 무슨 서비스인지 알 수 없다. "웹 앱이다"만 보인다.

도메인 중심 패키지 목록(`user / product / brand / like / order / stock`)을 보면 패키지 이름만으로 "이 시스템은 쇼핑몰이다"를 알 수 있다. 이를 **Screaming Architecture**라고 한다 — 패키지 구조 자체가 "이 시스템이 무엇을 하는가"를 드러내야 한다는 원칙이다.

| | 레이어 중심 | 도메인 중심 (약하게) |
|---|---|---|
| 기능 하나를 볼 때 | 여러 패키지를 오감 | 한 폴더 안에서 해결 |
| 시스템 정체성 | 패키지에서 안 보임 | 패키지 이름으로 보임 |
| 레이어 경계 가시성 | 높음 | import 경로로 확인 가능 |
| 새 기능 추가 시 | 여러 폴더에 파일 분산 생성 | 한 폴더 안에서 작업 |

**"약하게" 선택한 이유 — 레이어 서브패키지 유지**

완전한 도메인 중심은 레이어 서브패키지조차 없앤다.

```
like/
├── LikeModel.java
├── LikeFacade.java
├── LikeRepository.java
└── LikeV1Controller.java
```

응집도는 극대화되지만 레이어 경계가 사라진다. `LikeV1Controller`가 `LikeRepository`를 직접 import해도 패키지 구조상 막을 방법이 없다.

레이어 서브패키지를 유지하면 `com.loopers.like.interfaces.LikeV1Controller`가 `com.loopers.like.infrastructure.LikeJpaRepository`를 참조할 때 import 경로만 봐도 레이어 침범이 눈에 띈다. 단일 Gradle 모듈이라 빌드 시스템이 경계를 강제하지 못하는 상황에서, 패키지 이름이 그 역할을 대신한다.

---

> **결정 3–5 — 의존성 설계**
>
> 세 결정은 같은 질문의 세 가지 면이다: "의존성이 잘못된 방향으로 흐르면 어떻게 되나?"
>
> | 결정 | 축 | 규칙 |
> |------|-----|------|
> | 결정 3 | 수평 내부 — DomainService ↔ Repository | DomainService는 Repository를 주입받지 않는다 |
> | 결정 4 | 수직 — Domain ↔ Infrastructure | DIP로 의존 방향을 역전시킨다, 선택적 적용 |
> | 결정 5 | 수평 외부 — 도메인 간 | Facade가 타 도메인 로드·저장을 담당하고, Domain은 파라미터로만 받는다 |

---

## 결정 3. DomainService — 순수 도메인 로직

**결정**
- DomainService는 Repository 의존 없이 순수 도메인 로직만 담당한다.
- Facade가 로드한 도메인 객체를 파라미터로 받아 비즈니스 규칙을 수행하고, 도메인 객체를 반환한다.

**만약 DomainService가 Repository를 주입받으면?**

```java
// 잘못된 방향 — DomainService가 Repository를 주입받는 경우
@Component
public class BrandService {
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public void deleteCascade(Long brandId) {
        BrandModel brand = brandRepository.find(brandId).orElseThrow(...);
        List<ProductModel> products = productRepository.findAllByBrandId(brandId);
        products.forEach(ProductModel::delete);
        brand.delete();
    }
}
```

`BrandServiceTest`에서 "cascade 삭제 시 brand와 product가 모두 delete() 상태가 되는가?"라는 순수한 비즈니스 규칙을 검증하려면, Repository 두 개를 Mock해야 한다. 비즈니스 로직 테스트인데 인프라 세팅이 필요해진다.

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

`BrandServiceTest`는 Mock 없이 순수 Java로 작성한다:

```java
class BrandServiceTest {
    BrandService brandService = new BrandService();

    @Test
    void deleteCascade() {
        BrandModel brand = new BrandModel("브랜드", "설명");
        List<ProductModel> products = List.of(new ProductModel(...));

        brandService.deleteCascade(brand, products);

        assertThat(brand.isDeleted()).isTrue();
        assertThat(products.get(0).isDeleted()).isTrue();
    }
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

## 결정 4. DIP 적용 범위

**결정**
- 적용: `Repository` 인터페이스, `PasswordEncryptor` 인터페이스
- 미적용: `Facade`, `DomainService`, `Specification`, `Policy`

**의존 방향 역전**

구체 클래스를 직접 사용하면 상위 레이어가 인프라를 알아야 한다:

```
domain → infrastructure (domain이 JPA 구현체를 직접 참조)
```

Repository를 인터페이스로 두고 `domain` 레이어에 위치시키면:

```java
// product/domain/ — 인터페이스 정의 (domain이 소유)
public interface ProductRepository { ... }

// product/infrastructure/ — 구현체 (infrastructure가 domain을 바라봄)
public class ProductRepositoryImpl implements ProductRepository { ... }
```

infrastructure가 domain을 바라보는 방향으로 역전된다:

```
infrastructure → domain (구현체가 인터페이스를 구현)
```

이 덕분에 테스트에서 Fake/Mock 구현체로 교체할 수 있다.

**선택적 적용 기준**

"테스트나 교체를 위해 구현체를 바꿀 가능성이 있는가?"

| 대상 | 교체 가능성 | DIP 적용 |
|------|-----------|---------|
| Repository | Fake/Mock으로 교체 (테스트) | ✅ |
| PasswordEncryptor | NoOp으로 교체 (테스트) | ✅ |
| Facade | 교체 시나리오 없음 | ❌ |
| DomainService | 교체 시나리오 없음 | ❌ |
| Specification / Policy | 교체 시나리오 없음 | ❌ |

모든 경계에 인터페이스를 두면 헥사고날 아키텍처가 된다. 파일 수가 두 배로 늘어나지만 "인프라 교체 가능성"이라는 이익이 실현되는 경우는 드물다.

---

## 결정 5. 도메인 간 의존 원칙

**확정된 원칙**

| 레이어 | 허용 | 금지 |
|--------|------|------|
| Domain | 파라미터로 전달받은 타 도메인 Model / Specification 사용 | 타 도메인 Service / Repository 직접 참조 |
| Application | Repository 로드·저장, DomainService 호출 | 크로스 도메인 비즈니스 로직 인라인 |

금지 대상이 Service와 Repository인 이유:
- **Service** — 타 도메인 비즈니스 규칙에 결합된다
- **Repository** — 인프라 의존성이 딸려 들어온다

Specification은 순수 Java 판단 로직이므로 도메인 레이어 간 참조가 허용된다. `like/domain/LikeRegistrationPolicy`가 `product/domain/ProductExistsSpecification`을 import하는 것이 이에 해당한다.

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

**각 규칙을 어기면**

| 위반 | 결과 |
|------|------|
| DomainService가 Repository를 주입받음 (결정 3) | DomainService 단위 테스트 시 Repository Mock 필요 |
| DIP 미적용, 구체 클래스 직접 사용 (결정 4) | 테스트에서 실제 DB 연결 필요 |
| Domain이 타 도메인 Service/Repository 직접 참조 (결정 5) | 도메인 경계 붕괴, 변경 파급 범위 예측 불가 |

---

## 결정 6. 트랜잭션 경계

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

## 결정 7. Value Object — Price

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

## 결정 8. Specification · Policy 패턴 — 조건과 정책 캡슐화

**등장 배경**

조건 검사를 Facade에 인라인으로 작성하면 두 가지 문제가 생긴다.

```java
// Before — LikeFacade에 조건 검사 인라인
public LikeInfo addLike(Long userId, Long productId) {
    Optional<ProductModel> product = productRepository.find(productId);
    if (product.isEmpty()) throw new CoreException(NOT_FOUND, "상품이 존재하지 않습니다.");

    Optional<LikeModel> existing = likeRepository.findByUserIdAndProductId(userId, productId);
    if (existing.isPresent()) throw new CoreException(CONFLICT, "이미 좋아요한 상품입니다.");
    ...
}
```

1. **정책에 이름이 없다** — "좋아요 등록 가능 여부"라는 비즈니스 규칙이 어디에 있는지 Facade를 다 읽어야 알 수 있다.
2. **재사용할 수 없다** — 같은 조건이 다른 흐름에서 필요하면 복붙해야 한다.

---

**Specification — "이 조건을 만족하는가?"**

```java
// support/Specification.java
public interface Specification<T> {
    boolean isSatisfiedBy(T candidate);
}
```

| 클래스 | 위치 | 판단 내용 |
|---|---|---|
| `ProductExistsSpecification` | `product/domain/` | `product.isPresent()` |
| `LikeNotDuplicateSpecification` | `like/domain/` | `existing.isEmpty()` |
| `OrderOwnerSpecification` | `order/domain/` | `order.getUserId().equals(userId)` |

핵심 원칙: **boolean만 반환, 예외 없음**

Specification은 판단만 하고, 판단 결과에 따른 처리는 호출부 책임이다. 예외가 Specification 안에 있으면 맥락에 따라 다르게 쓸 수 없다.

```java
// 맥락 A: 예외 발생
if (!productExists.isSatisfiedBy(product)) throw new CoreException(NOT_FOUND, ...);

// 맥락 B: 필터링 (예외가 내부에 있었다면 불가능)
products.stream().filter(p -> productExists.isSatisfiedBy(Optional.of(p)))
```

Specification은 상태 없는 순수 판단 로직이라 `@Component` 없이 `new`로 생성한다.

**`OrderOwnerSpecification` — 판단 기준이 외부 맥락에 의존할 때**

앞의 두 Specification은 파라미터 하나로 판단이 완료된다. "이 주문이 이 사용자의 것인가?"는 비교 대상인 `userId`가 외부에서 와야 한다.

```java
public class OrderOwnerSpecification implements Specification<OrderModel> {
    private final Long userId;  // 생성 시점에 맥락 주입

    public OrderOwnerSpecification(Long userId) { this.userId = userId; }

    @Override
    public boolean isSatisfiedBy(OrderModel order) {
        return order.getUserId().equals(userId);
    }
}

// OrderService — 생성과 판단을 한 줄로
public void checkOwnership(OrderModel order, Long userId) {
    if (!new OrderOwnerSpecification(userId).isSatisfiedBy(order)) {
        throw new CoreException(FORBIDDEN, "본인의 주문만 조회할 수 있습니다.");
    }
}
```

---

**Policy — "이 행위가 가능한가?"**

여러 Specification이 함께 충족되어야 하는 비즈니스 정책은 Policy로 묶는다.

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

Specification과 다른 점:
- **예외를 던진다** — Policy는 위반 시 어떻게 되는지(에러 코드·메시지)를 안다
- **순서를 보장한다** — 상품이 없는데 중복 여부를 확인하는 건 의미 없다
- **`@Component`다** — Facade에 주입받아야 하므로 Spring이 관리한다

**Facade 인라인 제거 효과**

```
Before:
  LikeFacade  → if (product.isEmpty()) throw NOT_FOUND      // 인라인
  LikeService → if (existing.isPresent()) throw CONFLICT    // 인라인
  LikeService → createLike()  // 중복 검증도 여기에 섞여 있었음

After:
  LikeFacade  → likeRegistrationPolicy.check(product, existing)
                   ├─ ProductExistsSpecification            // 상품 존재 확인
                   └─ LikeNotDuplicateSpecification        // 중복 확인
  LikeService → createLike(userId, productId)              // 순수 생성만
```

---

**선택 기준 — getOrThrow · Specification · Policy**

| 상황 | 패턴 | 이유 |
|---|---|---|
| 존재 확인 후 엔티티가 필요함 | `getOrThrow()` | Optional 언래핑이 목적 |
| 단일 boolean 판단, 재사용 가능성 있음 | `Specification` | 판단 로직을 이름 붙여 캡슐화 |
| 두 조건 이상을 묶어 하나의 정책으로 표현 | `Policy` | 정책 이름, 순서, 예외를 한 곳에 |

**도입 기준**
- Specification: 순수 boolean 판단이고, 같은 조건이 여러 맥락에서 쓰일 가능성이 있는 경우
- Policy: 두 개 이상의 조건이 묶여야 하나의 행위 가능 여부가 결정되는 경우

---

## 결정 9. 예외 처리 전략

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

## 결정 10. likeCount 업데이트 — 원자 쿼리

**결정**
- Repository의 `@Modifying @Query`로 원자적으로 처리한다.

**이유**
- 동시 좋아요 요청 시 Java 필드 수정 + JPA dirty checking은 Lost Update가 발생한다.
- `UPDATE SET like_count = like_count + 1`은 DB가 원자적으로 처리해 동시성이 안전하다.

**주의**
- 좋아요 등록·취소 후 `productRepository.incrementLikeCount()` / `decrementLikeCount()`를
  LikeFacade에서 반드시 명시적으로 호출해야 한다.

---

## 결정 11. 기타 결정

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

## 결정 12. Stock — 독립 도메인 분리

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
