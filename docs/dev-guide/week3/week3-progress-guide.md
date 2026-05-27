# Week 3 — 구현 현황 & 가이드

> 작성 기준일: 2026-05-25  
> 과제 원문: `week3-assignment.md`

---

## 1. 체크리스트별 현황

### 🏷 Product / Brand 도메인

| 항목 | 상태 | 현황 |
|------|:----:|------|
| 상품 정보 객체에 브랜드 정보·좋아요 수 포함 | ❌ | `ProductInfo`에 `brandName`, `likeCount` 없음 |
| 정렬 조건(`latest` / `price_asc` / `likes_desc`) 조회 설계 | ❌ | `getAllProducts()`가 단순 전체 조회, 정렬·페이지네이션 없음 |
| 상품이 재고를 갖고 주문 시 차감 가능 | ❌ | `ProductModel`에 `stock` 인라인 — `Stock` 미분리, 차감 메서드 없음 |
| 재고 음수 방지를 도메인 레벨에서 처리 | ❌ | 차감 로직 자체 없음 |
| Brand 도메인 | ⚠️ | 껍데기(TODO) 생성 완료, 내부 로직 미구현 |

### 👍 Like 도메인

| 항목 | 상태 | 현황 |
|------|:----:|------|
| Like 도메인 분리 (Model / Repository / Service) | ❌ | 파일 없음 |
| 상품 조회 시 좋아요 수 제공 | ❌ | — |
| 등록 / 취소 단위 테스트 | ❌ | — |

### 🛒 Order 도메인

| 항목 | 상태 | 현황 |
|------|:----:|------|
| Order / OrderItem 도메인 | ❌ | 파일 없음 |
| 주문 시 재고 차감 | ❌ | — |
| 예외 흐름 (재고 부족 / 상품 없음 등) 설계 | ❌ | — |
| 정상 / 예외 주문 단위 테스트 | ❌ | — |

### 🧩 도메인 서비스

| 항목 | 상태 | 현황 |
|------|:----:|------|
| 도메인 간 협력 로직 Domain Service 위치 | ❌ | 도메인 간 연계 없음 |
| `Product + Brand` 조합 도메인 서비스 처리 | ❌ | ProductService가 Brand를 모름 |
| 복합 유스케이스 → Application Layer orchestration | ⚠️ | ProductFacade 있으나 Brand·Like 미조합 |

### 🧱 아키텍처 & 설계

| 항목 | 상태 | 현황 |
|------|:----:|------|
| 4-레이어 패키지 구조 | ✅ | interfaces / application / domain / infrastructure |
| Repository Interface → Domain, 구현체 → Infra | ✅ | 패턴 확립됨 |
| 패키지 구성 (계층 + 도메인) | ✅ | |
| Application Layer orchestration | ⚠️ | `UserV1Controller`가 Facade 없이 `UserService` 직접 호출 중 |
| 단위 테스트 (Mockito Stub 활용) | ⚠️ | User 도메인만 작성됨 |

---

## 2. 현재 코드와 설계문서 간 불일치

### ProductModel (가장 시급)

| 항목 | 현재 코드 | 설계 (`04-erd.md`) |
|------|-----------|-------------------|
| `description` 필드 | 있음 | **없음** |
| `brand_id` FK | **없음** | 있음 (NOT NULL) |
| `stock` 필드 | ProductModel 인라인 | **`stocks` 테이블로 분리** |
| `deleted_at` | BaseEntity 상속으로 있음 | 있음 ✅ |
| `price` 타입 | `Long` | `INT` |
| `price > 0` 조건 | `>= 0` 체크 | `> 0` 이어야 함 |
| 물리 삭제 | `productRepository.delete(id)` | **소프트 딜리트** |

---

## 3. 구현 가이드

### 3-1. 구현 순서 (의존성 기준)

```
① Brand (루트, 외부 의존 없음)
      ↓
② Product 재설계 (brand_id FK 필요)
      ↓
③ Stock (product_id FK 필요)
      ↓
④ Like (user_id + product_id FK 필요)
      ↓
⑤ Order + OrderItem (user_id + product_id + Stock 필요)
```

---

### 3-2. Brand 구현

**껍데기 파일 위치**
```
domain/brand/       BrandModel, BrandRepository, BrandService
application/brand/  BrandFacade, BrandInfo, BrandCreateCommand, BrandUpdateCommand
infrastructure/brand/ BrandJpaRepository, BrandRepositoryImpl
interfaces/api/brand/ BrandV1Controller, BrandAdminV1Controller, BrandV1Dto
```

**핵심 구현 포인트**

```java
// BrandModel — 생성자 검증
public BrandModel(String name, String description) {
    if (name == null || name.isBlank())
        throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 필수입니다.");
    this.name = name;
    this.description = description;
}

// BrandModel — isDeleted()는 BaseEntity.deletedAt 활용
public boolean isDeleted() {
    return getDeletedAt() != null;
}
```

```java
// BrandService — 중복 이름 체크
public BrandModel create(BrandModel brand) {
    if (brandRepository.existsByName(brand.getName()))
        throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
    return brandRepository.save(brand);
}

// BrandService — 조회 시 삭제 여부 확인
public BrandModel getById(Long id) {
    return brandRepository.findById(id)
        .filter(b -> !b.isDeleted())
        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
}
```

```java
// BrandFacade — 삭제 시 소속 상품 연쇄 소프트딜리트 조율
public void delete(Long id) {
    brandService.delete(id);
    productService.deleteAllByBrandId(id); // Product 구현 후 연결
}
```

**단위 테스트 대상**
- `create()` — 정상 등록, 이름 중복 → CONFLICT
- `getById()` — 존재하지 않는 ID → NOT_FOUND, 삭제된 브랜드 → NOT_FOUND
- `update()` — 정상 수정, 존재하지 않는 ID → NOT_FOUND
- `delete()` — 소프트딜리트 후 isDeleted() == true

---

### 3-3. Product 재설계

**변경 사항**

```java
@Entity
@Table(name = "products")
public class ProductModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false, updatable = false) // 브랜드는 수정 불가
    private BrandModel brand;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private int price; // Long → int, > 0 조건

    // description 제거
    // stock 제거 → Stock 엔티티로 분리
}
```

**소프트딜리트 방식으로 전환**
```java
// ProductService — 물리 삭제 → 소프트딜리트
public void delete(Long id) {
    ProductModel product = getById(id);
    product.delete(); // BaseEntity.delete()
}

// 브랜드별 전체 소프트딜리트 (Brand 삭제 연쇄용)
public void deleteAllByBrandId(Long brandId) {
    productRepository.findAllByBrandId(brandId)
        .forEach(ProductModel::delete);
}
```

**정렬·페이지네이션 조회**
```java
// ProductRepository
Page<ProductModel> findAll(ProductSearchCondition condition, Pageable pageable);

// ProductSearchCondition
public record ProductSearchCondition(Long brandId, SortType sort) {}

public enum SortType { LATEST, PRICE_ASC, LIKES_DESC }
```

> `LIKES_DESC` 정렬은 선택 구현이므로 우선 `LATEST`, `PRICE_ASC`만 구현해도 무방

---

### 3-4. Stock 구현

**엔티티 설계**
```java
@Entity
@Table(name = "stocks")
public class StockModel extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private ProductModel product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public boolean hasEnough(int requestedQuantity) {
        return this.quantity >= requestedQuantity;
    }

    public void decrease(int quantity) {
        if (!hasEnough(quantity))
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        this.quantity -= quantity;
    }
}
```

> `decrease()`의 음수 방지 검증이 도메인 레벨에 있어야 체크리스트 조건 충족

**단위 테스트 대상**
- `hasEnough()` — 충분한 재고, 정확히 같은 재고, 부족한 재고
- `decrease()` — 정상 차감, 재고 부족 → BAD_REQUEST, 차감 후 0 확인

---

### 3-5. Like 구현

**엔티티 설계**
```java
@Entity
@Table(name = "likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
public class LikeModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "liked_at", nullable = false)
    private ZonedDateTime likedAt;
}
```

**멱등 처리 포인트**
```java
// LikeService
public void like(Long userId, Long productId) {
    productService.getById(productId); // 상품 존재·삭제 여부 확인
    if (likeRepository.existsByUserIdAndProductId(userId, productId)) return; // 멱등
    likeRepository.save(new LikeModel(userId, productId));
}

public void unlike(Long userId, Long productId) {
    likeRepository.findByUserIdAndProductId(userId, productId)
        .ifPresent(likeRepository::delete); // 없으면 그냥 통과 — 멱등
}

public long countByProductId(Long productId) {
    return likeRepository.countByProductId(productId);
}
```

**단위 테스트 대상**
- `like()` — 신규 등록, 중복 등록(멱등 — 에러 없음), 삭제된 상품 → NOT_FOUND
- `unlike()` — 정상 취소, 이미 없는 좋아요(멱등 — 에러 없음)

---

### 3-6. Order 구현

**엔티티 설계 포인트**

```java
@Entity @Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status; // PENDING / CONFIRMED / CANCELLED

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemModel> items = new ArrayList<>();
}

@Entity @Table(name = "order_items")
public class OrderItemModel extends BaseEntity {
    // 주문 도메인이 자체 소유하는 값 (snapshot_ 접두사 사용 안 함)
    private Long productId;      // 원본 참조용
    private String productName;  // 주문 시점 합의값
    private int unitPrice;       // 주문 시점 합의 단가
    private String brandName;    // 주문 시점 합의값
    private int quantity;
}
```

**주문 생성 흐름 (Application Layer에서 orchestration)**

```java
// OrderFacade (Application Layer)
@Transactional
public OrderInfo createOrder(Long userId, List<OrderItemCommand> items) {
    // 1. 전체 상품 조회 + 삭제 여부 확인
    // 2. 전체 재고 사전 확인 (한 건이라도 부족하면 전체 실패)
    // 3. 재고 일괄 차감
    // 4. 주문 생성 + OrderItem 스냅샷 저장
}
```

> 재고 확인과 차감을 같은 트랜잭션 안에서 처리해야 정합성 보장

**단위 테스트 대상**
- `createOrder()` — 정상 주문, 재고 부족 → BAD_REQUEST(전체 실패), 존재하지 않는 상품 → NOT_FOUND
- `OrderItemModel` — quantity >= 1 검증
- `StockModel.decrease()` — 음수 방지 (위에서 다룸)

---

## 4. 공통 패턴 정리

### 레이어 간 흐름

```
Controller
  → (Dto.toCommand())
  → Facade          ← Application Layer: 도메인 조합 orchestration
  → Service(s)      ← Domain Layer: 단일 도메인 비즈니스 로직
  → Repository      ← Domain Interface
  → RepositoryImpl  ← Infrastructure: JPA 위임
```

### 단위 테스트 구조 (User 도메인 참고)

```java
@ExtendWith(MockitoExtension.class)
class XxxServiceTest {

    @InjectMocks XxxService xxxService;
    @Mock XxxRepository xxxRepository;

    @Test
    void 정상_케이스() {
        // Arrange
        given(...).willReturn(...);

        // Act
        XxxModel result = xxxService.doSomething(...);

        // Assert
        assertThat(result.xxx()).isEqualTo(...);
        then(xxxRepository).should().save(...);
    }
}
```

### 소프트딜리트 조회 주의

`BaseEntity.deletedAt`은 필드만 제공하므로, **조회 시 항상 삭제 여부를 필터해야 합니다.**

```java
// 방법 A: Repository 메서드에서 필터 (JPA Query)
Optional<BrandModel> findByIdAndDeletedAtIsNull(Long id);

// 방법 B: Service 레이어에서 filter
brandRepository.findById(id)
    .filter(b -> !b.isDeleted())
    .orElseThrow(...);
```

> 일관성을 위해 **방법 A** (Repository 쿼리 레벨 필터) 권장

---

## 5. 남은 작업 체크리스트

```
[ ] Brand — 내부 로직 구현 + 단위 테스트
[ ] Product — brand_id 추가, description 제거, 소프트딜리트 전환, 정렬/페이지네이션
[ ] Stock — 엔티티·서비스 구현 + 음수 방지 단위 테스트
[ ] Like — 엔티티·서비스 구현 + 멱등 단위 테스트
[ ] Order / OrderItem — 엔티티·Facade 구현 + 재고 차감 흐름 단위 테스트
[ ] ProductInfo — brandName, likeCount 포함
[ ] ProductFacade — Brand + Like 조합 orchestration
```
