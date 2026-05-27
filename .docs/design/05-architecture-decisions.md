# 05. Architecture Decisions — 아키텍처 결정 기록

> 이 문서는 구현 전 설계 단계에서 내린 아키텍처 결정과 그 이유를 기록합니다.
> "왜 이렇게 했지?"를 나중에 떠올릴 때 참조하세요.

---

## 결정 1. 레이어드 아키텍처 (4티어)

**결정**
```
interfaces → application → domain ← infrastructure
```

**선택지 비교**

| 기준 | 레이어드 | 헥사고날 / 클린 |
|------|----------|-----------------|
| 구조 | 레이어를 위→아래로 쌓음 | 도메인을 중심에 두고 Port/Adapter로 감쌈 |
| 의존 방향 | 상위 → 하위 (단방향) | 모두 도메인 안쪽을 향함 (Dependency Rule) |
| DIP 적용 범위 | 필요한 곳에만 선택 적용 | 모든 외부 접점에 Port 인터페이스 필수 |
| 학습 비용 | 낮음 — 레이어 개념만 이해하면 됨 | 높음 — Port/Adapter/UseCase 개념 추가 필요 |
| 인프라 교체 용이성 | 인터페이스 적용 범위에 따라 다름 | 모든 외부 접점이 격리되어 교체 용이 |
| 코드 복잡도 | 낮음 | 높음 — 인터페이스·구현체 파일 수 증가 |
| 적합한 상황 | 일반적인 웹 서비스, 소규모 팀 | 인프라 교체 가능성이 높거나 복잡한 도메인 |

세 방식 모두 도메인 로직을 외부 관심사로부터 격리한다는 철학은 동일하다.
차이는 그 격리를 얼마나 엄격하게, 어떤 구조로 강제하느냐에 있다.

**이유**
- 헥사고날/클린은 모든 외부 접점에 Port 인터페이스를 두어 격리 수준이 높지만,
  그만큼 인터페이스·구현체 파일이 늘어나고 팀 전체가 Port/Adapter 개념을 숙지해야 한다.
- 본 프로젝트에서 인프라(DB, 외부 API)를 교체할 가능성이 낮고, 규모가 크지 않다.
- DIP는 실제 교체 가능성이 있는 `Repository`, `PasswordEncryptor`에만 적용해 복잡도를 낮춘다. (→ 결정 3 참고)
- 레이어 간 의존 방향을 단방향으로 강제해 변경 영향 범위를 제한하는 것만으로 충분하다고 판단했다.
- Application Layer(Facade)는 Repository 로드·저장을 담당한다.
- 도메인 간 협력 비즈니스 로직은 Domain Layer(DomainService)에서 처리한다.
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

**선택지 비교**

| 기준 | Layer-first | Domain-first |
|------|-------------|--------------|
| 패키지 최상위 | 레이어 (`controller/`, `service/`, ...) | 도메인 (`user/`, `brand/`, ...) |
| 관련 코드 위치 | 여러 레이어 폴더에 분산 | 한 도메인 폴더 안에 집중 |
| 레이어 경계 | 패키지 자체가 경계 → 직관적 | 서브패키지로 표현 → 규율 필요 |
| 기능 추가 시 | 여러 폴더에 파일을 나눠서 생성 | 한 도메인 폴더 안에서 완결 |
| 팀 협업 | 같은 레이어 폴더에서 충돌 가능 | 도메인별 작업 영역 분리 |
| 마이크로서비스 전환 | 도메인 파일이 분산되어 수집 필요 | 도메인 폴더 단위로 분리 가능 |
| 비즈니스 언어 일치 | 레이어 중심 → 기술적 구분 | 도메인 중심 → 비즈니스 언어와 일치 |

두 방식 모두 4티어 레이어드 아키텍처를 표현할 수 있고 실무에서 충분히 검증된 구조다.

**이유**
- 두 방식 모두 유효하나, Layer-first는 이미 경험이 충분하므로 Domain-first를 직접 경험해 본다.
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
- 여러 도메인에 걸친 협력 비즈니스 로직은 Domain Layer(DomainService)에서 처리한다.
- DomainService는 Facade가 로드한 여러 도메인의 객체를 파라미터로 받아 비즈니스 규칙을 수행한다.
  (Repository를 직접 주입받지 않는 원칙은 유지된다 — 결정 11 참고)
- Facade는 Repository 로드·저장과 DomainService 호출 순서 조율만 담당한다.

**흐름**
```
Facade: 여러 Repository에서 도메인 객체 로드
  → DomainService: 도메인 객체를 파라미터로 받아 크로스 도메인 비즈니스 로직 수행
  → Facade: 변경된 도메인 객체를 Repository에 저장
```

**이유**
- "재고 차감을 어떻게 하는가", "삭제 연쇄를 어떻게 처리하는가"는 비즈니스 규칙이다.
  비즈니스 규칙은 Application Layer가 아닌 Domain Layer에 위치해야 한다.
- Facade가 크로스 도메인 로직을 인라인으로 처리하면, 동일 규칙이 여러 Facade에 분산될 위험이 있다.
- DomainService에 위치하면 Repository 없이 순수 Java 단위 테스트로 검증 가능하다.

**트레이드오프**
- DomainService가 타 도메인의 Model을 파라미터로 받으므로 Domain 레이어에 cross-domain import가 생긴다.
  이는 결정 9에서 금지한 Service/Repository 직접 참조와 다른, **파라미터 수준의 약한 결합**으로 허용한다.
- 유스케이스 전체 흐름을 파악하려면 Facade와 DomainService 두 파일을 오가야 한다.

**예시 — 브랜드 삭제 연쇄 처리**

```java
// brand/domain/BrandService.java — Repository 없음, 크로스 도메인 비즈니스 로직 담당
public void deleteCascade(BrandModel brand, List<ProductModel> products, List<UserModel> admins) {
    products.forEach(ProductModel::softDelete);    // 상품 소프트 딜리트
    admins.forEach(UserModel::softDelete);         // BRAND_ADMIN 소프트 딜리트
    brand.softDelete();                            // 브랜드 소프트 딜리트
}

// brand/application/BrandFacade.java — 로드·저장 및 호출 순서 조율만 담당
@Transactional
public void deleteBrand(Long brandId) {
    BrandModel brand = brandService.getOrThrow(brandRepository.findById(brandId));
    List<ProductModel> products = productRepository.findAllByBrandId(brandId);
    List<UserModel> admins = userRepository.findAllByBrandId(brandId);

    brandService.deleteCascade(brand, products, admins); // 비즈니스 로직은 DomainService에 위임
    // 변경된 객체들은 JPA dirty checking으로 자동 반영
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

// product/infrastructure/ProductJpaRepository.java  ← @Modifying @Query는 JpaRepository에 위치
@Modifying
@Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
void incrementLikeCount(@Param("id") Long id);
```

**이유**
- 동시 좋아요 요청 시 `likeCount++` (Java 필드 수정 + JPA dirty checking)은 Lost Update가 발생한다.
- `UPDATE SET like_count = like_count + 1`은 DB가 원자적으로 처리해 동시성이 안전하다.

**파생 결정**
- 클래스 다이어그램의 `ProductModel.likeAdded()` / `likeRemoved()`는 구현하지 않는다.
  필드를 직접 수정하지 않으므로 메서드 존재 이유가 없다.

**⚠️ 구현 시 주의 — 누락되기 쉬운 지점**
- 좋아요 등록·취소 후 `productRepository.incrementLikeCount()` / `decrementLikeCount()`를
  **반드시 LikeFacade에서 명시적으로 호출**해야 한다.
- JPA dirty checking은 `@Modifying @Query`로 실행된 원자 쿼리를 감지하지 않으므로,
  Facade에서 직접 호출하지 않으면 likeCount가 갱신되지 않는다.

```java
// like/application/LikeFacade.java
LikeInfo saved = LikeInfo.from(likeRepository.save(like));
productRepository.incrementLikeCount(productId);   // ← 명시적 호출 필수
return saved;
```

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

**⚠️ 현재 미이행**
- 현재 구현에서는 `modules/jpa/BaseEntity`에 `deletedAt`이 그대로 포함되어 있다.
- `SoftDeletableEntity` 분리 작업이 이루어지지 않아, `LikeModel`, `OrderModel`, `OrderItemModel`도
  소프트 딜리트 시나리오가 없음에도 불필요한 `deleted_at` 컬럼을 갖는다.
- ERD 설계 의도와 실제 구현이 불일치하는 상태이며, 향후 리팩토링 대상이다.

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
- Domain 레이어: 다른 도메인의 Service/Repository 직접 참조 금지.
  단, Facade가 로드해 파라미터로 건넨 타 도메인 Model은 받을 수 있다 (파라미터 수준의 약한 결합).
- Application 레이어: Repository 로드·저장과 DomainService 호출 순서 조율만 담당.
  크로스 도메인 비즈니스 로직을 인라인으로 직접 처리하지 않는다.
- `Application → Domain ← Infrastructure` 레이어 원칙 준수.

**전체 의존 방향 지도**

```
[Domain 레이어 — Service/Repository 수평 의존 없음, 파라미터로 타 도메인 Model 수신 가능]
product/domain  (brand/domain을 import하지 않음)
order/domain    ← (파라미터로) ProductModel 수신
brand/domain    ← (파라미터로) ProductModel, UserModel 수신

[Application 레이어 — 로드·저장·순서 조율만]
product/application ──► brand/domain    (브랜드 정보 로드 후 ProductService에 위임)
like/application    ──► product/domain  (상품 로드 후 LikeService에 위임)
order/application   ──► product/domain  (상품 로드 후 OrderService에 위임)
brand/application   ──► product/domain  (상품 로드 후 BrandService에 위임)
brand/application   ──► user/domain     (유저 로드 후 BrandService에 위임)
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
| DomainService | 순수 비즈니스 규칙 + 크로스 도메인 협력 로직 — 엔티티를 파라미터로 받아 검증·변환·조립·상태 변경 |
| Facade | Repository 로드·저장 + DomainService 호출 순서 조율 |

**예시**

```java
// order/domain/OrderService.java — Repository 없음, 크로스 도메인 비즈니스 로직 담당
public OrderModel createOrder(Long userId, List<ProductModel> products, Map<Long, Integer> quantities) {
    // 재고 차감 (Order → Product 크로스 도메인 규칙)
    products.forEach(p -> p.decreaseStock(quantities.getOrDefault(p.getId(), 0)));
    // 스냅샷 생성
    List<OrderItemModel> items = products.stream()
        .map(p -> new OrderItemModel(p.getId(), p.getName(), p.getPrice(), quantities.get(p.getId())))
        .toList();
    return new OrderModel(userId, items);
}

// order/application/OrderFacade.java — 로드·저장만 담당
@Transactional
public OrderInfo createOrder(Long userId, List<OrderItemRequest> requests) {
    Map<Long, Integer> quantities = ...;
    List<ProductModel> products = productRepository.findAllByIds(quantities.keySet()); // Facade가 로드

    OrderModel order = orderService.createOrder(userId, products, quantities); // 크로스 도메인 로직은 DomainService에 위임

    productRepository.saveAll(products); // 재고 변경 저장
    return OrderInfo.from(orderRepository.save(order));
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
| **Model / VO** | 비즈니스 규칙 위반 | `ProductModel.decreaseStock` → `Stock.decrease()`가 재고 부족 시 `BAD_REQUEST`; `Price` 생성 시 null·음수 검증 (→ 결정 16 참고) |
| **Service** | 존재 여부 검증 | `getOrThrow(Optional)` → 없으면 `NOT_FOUND` |
| **Facade** | DB 조회가 필요한 권한·크로스 도메인 검증 | 주문 소유권 확인 (DB 조회 후 userId 비교) → `FORBIDDEN` |
| **Interfaces** | HTTP 요청 형식 검증, DB 조회 없이 경로 변수만으로 판단 가능한 권한 확인 | `@Valid` DTO 검증; `userId` 불일치 → `FORBIDDEN` |

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

## 결정 14. Like 도메인 API URL 구조

**결정**

| 기능 | HTTP 메서드 | URL |
|------|------------|-----|
| 좋아요 등록 | `POST` | `/api/v1/products/{productId}/likes` |
| 좋아요 취소 | `DELETE` | `/api/v1/products/{productId}/likes` |
| 내 좋아요 목록 | `GET` | `/api/v1/users/{userId}/likes` |

**이유**
- 좋아요는 **상품 하위 리소스**다. `products/{productId}/likes`는 "해당 상품에 달린 좋아요"를 표현한다.
- 등록·취소 모두 productId가 경로에 있으므로 요청 body가 필요 없다.
- 내 좋아요 목록은 **유저 하위 리소스**다. `users/{userId}/likes`는 "해당 유저가 누른 좋아요 목록"을 표현한다.
- `GET /api/v1/likes/products`처럼 기능 중심으로 URL을 설계하면 리소스 중심 REST 원칙에서 벗어난다.

**파생 결정**
- `LikeV1Dto.AddLikeRequest` (productId 담는 body DTO)는 불필요해 삭제한다.
- 타인의 좋아요 목록 조회 시도 → FORBIDDEN 반환. (→ 결정 15 참고)

---

## 결정 15. 본인 소유 리소스 접근 시 FORBIDDEN 처리

**결정**
- 본인 소유가 아닌 리소스를 조회하려 할 때 `FORBIDDEN(403)`을 반환한다.
- `ErrorType.FORBIDDEN`을 신규 추가하고 `HttpStatus.FORBIDDEN(403)`으로 매핑한다.

**적용 대상**

| 엔드포인트 | 조건 | 처리 위치 |
|-----------|------|----------|
| `GET /api/v1/orders/{orderId}` | 주문의 userId ≠ 요청자 userId | `OrderFacade.getOrder()` |
| `GET /api/v1/users/{userId}/likes` | 경로 변수 userId ≠ 요청자 userId | `LikeV1Controller.getLikedProducts()` |

**구현**

```java
// order/application/OrderFacade.java
public OrderInfo getOrder(Long userId, Long orderId) {
    OrderModel order = orderService.getOrThrow(orderRepository.find(orderId));
    // [fix] 타인의 주문 조회 시 403 처리 누락
    if (!order.getUserId().equals(userId)) {
        throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.");
    }
    return OrderInfo.from(order);
}

// like/interfaces/LikeV1Controller.java
@GetMapping("/api/v1/users/{userId}/likes")
public ApiResponse<List<ProductInfo>> getLikedProducts(@CurrentUser LoginUser loginUser, @PathVariable Long userId) {
    if (!loginUser.id().equals(userId)) {
        throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
    }
    return ApiResponse.success(likeFacade.getLikedProducts(loginUser.id()));
}
```

**처리 레이어 선택 이유**
- 주문 소유권은 DB를 조회해야 알 수 있으므로 Facade(로드 후 비교)에서 처리한다.
- 좋아요 목록은 경로 변수만 비교하면 되므로 Controller에서 바로 처리한다.
  (결정 13의 레이어별 예외 책임 원칙과 일치한다: 권한은 Facade, HTTP 요청 형식은 Interfaces)

---

## 결정 16. Value Object — Price, Stock

**결정**
- `Price`(가격)와 `Stock`(재고)를 `product/domain/`에 Value Object(VO)로 분리한다.
- Java `record`로 구현하며, JPA Entity 필드(`Long price`, `Integer stock`)는 그대로 유지한다.

**도입 배경 — 중복 문제**

VO 도입 전, 동일한 검증 로직이 세 곳에 분산되어 있었다:

```java
// ProductModel 생성자
if (price == null) throw new CoreException(BAD_REQUEST, "가격은 비어있을 수 없습니다.");
if (price < 0)    throw new CoreException(BAD_REQUEST, "가격은 0 이상이어야 합니다.");

// ProductModel.update() — 동일 검증 반복
// OrderItemModel 생성자 — 동일 검증 반복
```

재고 차감 규칙(`decreaseStock`)도 `ProductModel` 안에 직접 구현되어 있어,
재고 개념의 규칙이 Entity 전체에 산재했다.

**설계**

```java
// product/domain/Price.java — 가격 검증 내재화
public record Price(Long value) {
    public Price {
        if (value == null) throw new CoreException(BAD_REQUEST, "가격은 비어있을 수 없습니다.");
        if (value < 0)    throw new CoreException(BAD_REQUEST, "가격은 0 이상이어야 합니다.");
    }
}

// product/domain/Stock.java — 재고 검증 + 차감 규칙 내재화
public record Stock(Integer value) {
    public Stock {
        if (value == null) throw new CoreException(BAD_REQUEST, "재고는 비어있을 수 없습니다.");
        if (value < 0)    throw new CoreException(BAD_REQUEST, "재고는 0 이상이어야 합니다.");
    }

    public Stock decrease(int quantity) {
        if (quantity <= 0) throw new CoreException(BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        if (this.value < quantity) throw new CoreException(BAD_REQUEST, "재고가 부족합니다.");
        return new Stock(this.value - quantity);
    }
}
```

**JPA 필드와의 관계**

`ProductModel`의 JPA 필드(`Long price`, `Integer stock`)는 그대로 유지한다.
DB 스키마 변경 없이 도입할 수 있으며, 생성·수정·차감 시 VO를 경유해 검증한 뒤 primitive 값을 저장한다.

```java
// ProductModel 생성자·update
this.price = new Price(price).value();
this.stock = new Stock(stock).value();

// ProductModel.decreaseStock
this.stock = new Stock(this.stock).decrease(quantity).value();
```

**효과**

| | Before | After |
|---|---|---|
| 가격 검증 | 생성자·`update`·`OrderItemModel` 3곳에 중복 | `Price` 1곳 |
| 재고 음수 방지 | `ProductModel`에 산재 | `Stock` 자체가 불변으로 보장 |
| 차감 규칙 | `ProductModel.decreaseStock`에 직접 구현 | `Stock.decrease()`로 캡슐화 |
| 검증 규칙 변경 시 | 여러 파일 수정 필요 | `Price` / `Stock` 1곳만 수정 |

**테스트 책임 분리**

| 테스트 파일 | 담당 |
|---|---|
| `PriceTest` | 가격 null·음수·0·양수 생성 케이스 |
| `StockTest` | 재고 null·음수·0·양수 생성 케이스 + 차감 케이스 |
| `ProductModelTest` | 상품명·설명 검증 (ProductModel 고유 책임) + 정상 생성·수정 확인 |

가격·재고 검증 테스트가 `ProductModelTest`에서 `PriceTest`/`StockTest`로 이동해 책임이 명확해졌다.

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
│   │   ├── SortCondition.java
│   │   ├── Price.java          ← VO
│   │   └── Stock.java          ← VO
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

