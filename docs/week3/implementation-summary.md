# Week 3 구현 요약

> Round 3 목표: 도메인 모델링을 통해 Product/Brand/Like/Order 핵심 개념을 Entity·VO·Domain Service로 표현하고, 레이어드 아키텍처 + DIP를 적용해 테스트 가능한 구조를 구성한다.

---

## 1. 작업 한눈에 보기

| 구분 | 작업 | 결과 |
|------|------|------|
| 룰 정비 | `CLAUDE.md` 확장 | 도메인 설계 전략 + 아키텍처 전략 섹션 추가 |
| 문서 정비 | 클래스 다이어그램·시퀀스·ERD 업데이트 | VO 도입, Domain Service 추가 반영 |
| 신규 구현 | VO 2개 + Domain Service 1개 + 도메인 객체 1개 | `Money`, `Quantity`, `ProductDetailService`, `ProductWithBrand` |
| 리팩토링 | 도메인 모델에 VO 적용, Facade-Domain Service 분리 | Product/Stock/Order/OrderItem/Payment + ProductFacade |
| 기능 추가 | 좋아요 수 집계 | `LikeService.countByProductId(Id)` / `countByProductIdIn(Ids)` |
| 테스트 | 단위 테스트 10개 작성 | Money/Quantity/Brand/Product/Stock/Order/OrderItem/Like Model + ProductDetailService + LikeService |
| 코드 리뷰 | 직접 셀프 리뷰 후 7개 이슈 발견 → 5개 수정 | N+1 fix, 미사용 코드 정리, 예외 일관성 등 |

---

## 2. 사전 작업 — `CLAUDE.md` 룰 확장

`CLAUDE.md`에 다음 두 섹션을 신규로 추가하여 Claude가 이번 주차 원칙을 일관되게 따르도록 했다.

### 2-1. 도메인 & 객체 설계 전략 (신규)

- 도메인 객체는 비즈니스 규칙을 캡슐화한다.
- Application 서비스는 도메인 조립/조정 역할에 한정한다.
- **규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높다.** (리팩토링 시그널)
- **Entity / Value Object / Domain Service 구분**
  - 원시 타입에 비즈니스 규칙이 반복되기 시작하면 VO 검토
  - "Manager/Doer"는 도메인이 아니라 서비스
- **도메인 로직 위치 휴리스틱** (4단계 판단 순서)
  1. 단일 객체 내부 → Entity 메서드
  2. 여러 도메인 협력 → Domain Service
  3. 유스케이스 흐름 조율 → Application Layer
  4. 외부 기술/시스템 → Infrastructure
- **Application Layer 경량 원칙**: Facade에 `if` 분기가 쌓이면 도메인으로 옮길 시점

### 2-2. 아키텍처 · 패키지 구성 전략 (신규)

- 레이어드 + DIP 준수
- API DTO ↔ Application DTO 분리
- 4 레이어 × 도메인 별 패키지 구성
- **DTO 분리 규칙**
  - `interfaces/api/{domain}/{Domain}V1Dto` — API DTO
  - `application/{domain}/{Domain}Info` — Application DTO
  - Domain Entity는 Application Layer 경계를 넘지 않는다
- **Aggregate 경계 규칙**: FK는 같은 애그리거트 내부에만, 그 외는 Long ID 참조
- **단위 테스트 의존성**: Fake/Stub 우선, Mockito는 호출 검증 한정

---

## 3. 설계 문서 업데이트 (Week 2 문서를 살아있는 문서로 유지)

### 3-1. `docs/week2/03-class-diagram.md`

- **Section 1 (도메인 모델)** — VO 2개 추가
  - `Money` (불변, 음수 방지, plus/minus/multiply/비교)
  - `Quantity` (불변, 음수 방지, plus/minus/비교)
  - 기존 엔티티 필드 타입 교체:
    - `Product.price`: `Long` → `Money`
    - `Stock.quantity`: `int` → `Quantity`
    - `Order.totalPrice`: `Long` → `Money`
    - `OrderItem.productPrice/quantity`: `Long/int` → `Money/Quantity`
    - `Payment.amount`: `Long` → `Money`
- **Section 3 (레이어별 구조)** — 재구성
  - 3-1: 레이어 개요 표 추가
  - 3-2: 모든 Service에 `<<Domain Service>>` 스테레오타입 명시 + `ProductDetailService` 신규 추가
  - 3-3: Application Facade 의존 구조 (신규) — Facade 5개 별도 다이어그램으로 분리

### 3-2. `docs/week2/02-sequence-diagrams.md`

- **Section 5 (상품 상세 조회)** — 완전 재작성
  - `ProductDetailService` (Domain Service) participant 추가
  - `LikeService` participant 추가
  - 4단계 흐름으로 명확화: ① Product+Brand 도메인 협력 → ② 재고 조회 → ③ 좋아요 수 집계 → ④ DTO 어셈블

### 3-3. `docs/week2/04-erd.md`

- VO ↔ DB 매핑 한 줄 추가: `Money` → `bigint`, `Quantity` → `int` (`@Embedded` 또는 `AttributeConverter`)

---

## 4. 신규 구현

### 4-1. Value Object — `domain/common/`

#### `Money.java`
```java
@Embeddable
public class Money {
    private Long amount;  // bigint 매핑

    // 생성: 음수 방지
    public static Money of(long amount);
    public static Money zero();

    // 연산: 불변, 새 인스턴스 반환
    public Money plus(Money other);
    public Money minus(Money other);    // 결과 음수 시 예외
    public Money multiply(int factor);  // 음수 factor 시 예외

    // 비교
    public boolean isGreaterThanOrEqual(Money other);
    public boolean isPositive();

    // equals/hashCode by amount
}
```

#### `Quantity.java`
- Money와 동일 패턴, `int value` 사용
- 재고/주문 수량 표현

### 4-2. Domain Service — `domain/product/`

#### `ProductWithBrand.java` (Domain Object)
```java
public record ProductWithBrand(ProductModel product, BrandModel brand) {}
```
- Product와 Brand가 함께 다뤄지는 도메인 단위
- UI DTO가 아님. 도메인 협력 결과
- `ProductDetailService`에서만 생성, 호출자가 존재성 보장하므로 null 검증 없음

#### `ProductDetailService.java` (Domain Service)
```java
@Service
public class ProductDetailService {
    public ProductWithBrand assemble(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return new ProductWithBrand(product, brand);
    }
}
```
- **단일 책임**: Product + Brand 도메인 객체 조합만 담당
- 재고/좋아요 수 같은 다른 애그리거트는 Application Facade에서 어셈블
- 상태 없음, 협력 중심

---

## 5. 리팩토링

### 5-1. 도메인 모델에 VO 적용 (5개 모델)

| 모델 | 변경 |
|------|------|
| `ProductModel` | `Long price` → `Money price` (`@Embedded`) |
| `StockModel` | `int quantity` → `Quantity quantity` |
| `OrderModel` | `Long totalPrice` → `Money totalPrice` |
| `OrderItemModel` | `Long productPrice` → `Money`, `int quantity` → `Quantity` |
| `PaymentModel` | `Long amount` → `Money amount` |

**접근 방식: 하이브리드**
- 내부 저장은 VO로 (`@Embedded`)
- 외부 노출 getter는 원시 타입 (`getPrice() : Long`) — DTO 호환성 유지
- 도메인 내부 협력 메서드는 VO 반환 (`calculateSubtotalAsMoney()`) — 패키지-private 으로 노출 범위 제한

```java
// 예시 — OrderItemModel
@Embedded private Money productPrice;
@Embedded private Quantity quantity;

// 도메인 내부 협력용 (같은 패키지의 OrderModel에서만 호출)
Money calculateSubtotalAsMoney() {
    return productPrice.multiply(quantity.getValue());
}

// DTO 호환용 (public)
public Long calculateSubtotal() {
    return calculateSubtotalAsMoney().getAmount();
}
public Long getProductPrice()  { return productPrice.getAmount(); }
public int getQuantity()       { return quantity.getValue(); }
```

### 5-2. `ProductFacade.getProduct` → `getProductDetail`

**Before**: Facade에서 직접 Product/Stock/Brand 조회·조합
```java
public ProductInfo getProduct(Long productId) {
    ProductModel product = productService.getProduct(productId);
    StockModel stock = stockService.getStock(productId);
    BrandModel brand = brandService.getBrand(product.getBrandId());
    return ProductInfo.forUser(product, stock, brand.getName());
}
```

**After**: Domain Service에 위임 + Facade는 어셈블만
```java
public ProductInfo getProductDetail(Long productId) {
    ProductWithBrand pwb = productDetailService.assemble(productId);   // 도메인 협력
    StockModel stock = stockService.getStock(productId);
    long likeCount = likeService.countByProductId(productId);
    return ProductInfo.forUser(pwb, stock, likeCount);                 // DTO 어셈블
}
```

추가로 메서드 이름을 `getProductDetail`로 변경하여 "단순 Entity 조회가 아니라 4개 도메인 어셈블 결과"라는 의도를 코드에서 드러나게 함. (`ProductV1Controller`도 함께 변경)

### 5-3. 좋아요 수 집계 기능 추가

- `LikeRepository.countByProductId(Long)` — 단건 집계
- `LikeRepository.countByProductIdIn(List<Long>)` — **일괄 집계 (N+1 회피)** → `Map<productId, count>` 반환
- `LikeJpaRepository`에 GROUP BY 쿼리 추가
- `LikeService`에 두 메서드 추가
- `ProductInfo.likeCount` 필드 추가
- `LikeFacade`/`ProductFacade` 둘 다 일괄 조회 사용 (N+1 회피)

---

## 6. 단위 테스트

총 10개 테스트 파일, 합산 약 70개 케이스 추가.

| 테스트 | 검증 내용 |
|--------|-----------|
| `MoneyTest` | 생성 검증, plus/minus/multiply, 비교, equals |
| `QuantityTest` | 동일 패턴 |
| `BrandModelTest` | 생성·수정·삭제 검증 |
| `ProductModelTest` | 생성·수정·삭제 + Money VO 검증 위임 |
| `StockModelTest` | 차감/복구/표시 정책 + Quantity VO 검증 위임 |
| `OrderItemModelTest` | 생성·소계 계산 + Money/Quantity 검증 |
| `OrderModelTest` | 생성·항목 추가·총액 계산·상태 전이 |
| `LikeModelTest` | 생성 |
| `LikeServiceTest` | **멱등성** (이미 좋아요/UK 위반/미존재 상품) + countByProductId |
| `ProductDetailServiceTest` | Mockito로 ProductService/BrandService 대체 → 협력 검증 |

모두 Spring 컨텍스트 없이 실행되는 순수 단위 테스트.

---

## 7. 코드 리뷰에서 발견·수정한 이슈

셀프 리뷰로 7개 이슈 발견, 5개 수정 + 2개는 의도적으로 그대로.

### 🔴 Major

| # | 이슈 | 조치 |
|---|------|------|
| ① | **좋아요 수 N+1** — `ProductFacade.getProducts`, `LikeFacade.getLikedProducts`가 stream 안에서 `countByProductId(id)`를 상품마다 호출 | `countByProductIdIn(List<Long>)` 추가 + 일괄 조회로 전환. 좋아요 0개는 `getOrDefault(id, 0L)` |

### 🟡 Medium

| # | 이슈 | 조치 |
|---|------|------|
| ② | 미사용 VO getter (`getPriceAsMoney`, `getQuantityAsValue`, `getAmountAsMoney`) | 삭제. 도메인 내부 협력 메서드(`calculateSubtotalAsMoney` 등)는 **`package-private`** 으로 노출 범위 좁힘 |
| ③ | `ProductWithBrand`의 `IllegalArgumentException` (프로젝트는 `CoreException` 일관성) | 호출자가 존재성 보장하므로 **검증 자체 삭제** (방어 과잉 해소) |
| ④ | `ProductDetailServiceTest`, `LikeServiceTest`의 `setUp()`에 `@BeforeEach` 누락 | 어노테이션 추가 + 수동 호출 제거 |

### 🟢 Minor

| # | 이슈 | 조치 |
|---|------|------|
| ⑤ | `Money.plus/multiply` overflow 가능성 | 무시 (현실적 발생 안 함) |
| ⑥ | `Money.minus` 중복 검증 | 유지 (에러 메시지 차별화 의도) |
| ⑦ | `ProductModelTest.brandIdIsImmutable` (시그니처가 이미 보장하는 약한 메타 테스트) | 삭제 |

---

## 8. 핵심 설계 결정 정리

### 8-1. VO 도입 방식: 하이브리드
- **내부**: `@Embedded` Money/Quantity (검증·연산 캡슐화)
- **외부**: `getPrice():Long`, `getQuantity():int` (DTO 호환성 유지)
- **도메인 협력**: `calculateSubtotalAsMoney():Money` — `package-private`로 노출 범위 제한

이유: API 응답 스펙 변경 없이 도메인 무결성을 확보. 가격/수량 음수 방지 같은 규칙이 도메인 곳곳에 반복 노출되는 안티패턴을 차단.

### 8-2. Domain Service vs Application Facade 경계

| 계층 | 책임 | 본 프로젝트 예 |
|------|------|---------------|
| **Domain Service** | 도메인 객체끼리의 협력 (도메인 개념 생성) | `ProductDetailService.assemble()` → `ProductWithBrand` |
| **Application Facade** | 유스케이스 흐름 조율 + DTO 어셈블 | `ProductFacade.getProductDetail()` → `ProductInfo` |

**판단 기준**: 결과물이 **도메인 개념(`ProductWithBrand`)이냐 UI DTO(`ProductInfo`)냐**

### 8-3. N+1 회피 패턴
- **목록 조회는 IN 쿼리 일괄 조회 원칙**
- `StockRepository.findAllByProductIdIn` (기존)
- `LikeRepository.countByProductIdIn` (이번 주 추가)
- ERD/클래스 다이어그램에서 명문화한 원칙을 코드에도 일관 적용

### 8-4. Facade 메서드 이름의 의도 표현
- `getProduct` → `getProductDetail`로 변경
- "단순 Entity 1건 조회"와 "여러 도메인 어셈블"을 이름으로 구분
- `ProductDetailService` (Domain Service)와 네이밍 일관성

---

## 9. Round 3 Quest 체크리스트 검증

### 🏷 Product / Brand
- ✅ 상품 정보 객체는 브랜드 정보, 좋아요 수를 포함 (`ProductInfo.forUser`)
- ✅ 정렬 조건 `latest/price_asc/likes_desc` 설계 유지
- ✅ 상품-재고 차감 (`Stock.deduct()`)
- ✅ **재고 음수 방지가 도메인 레벨**: `Stock.deduct()` + `Quantity.minus()` 이중 방어

### 👍 Like
- ✅ User ↔ Product 관계로 별도 도메인 분리 (기존)
- ✅ 좋아요 수 상품 상세/목록에서 제공 (`likeCount` 필드 신규)
- ✅ 등록/취소/멱등성 단위 테스트 (`LikeServiceTest`)

### 🛒 Order
- ✅ 다중 상품 + 수량 (`OrderItemModel`)
- ✅ 재고 차감 협력 (기존 `OrderService`)
- ✅ 예외 흐름 (유저/상품 부재, 재고 부족) 단위 테스트

### 🧩 Domain Service
- ✅ **Product + Brand 조합은 Domain Service**: `ProductDetailService`
- ✅ Application Facade는 흐름 조율 + DTO 어셈블만
- ✅ Domain Service는 상태 없음

### 🧱 아키텍처
- ✅ Application → Domain ← Infrastructure
- ✅ Repository Interface는 Domain Layer
- ✅ 패키지: 계층 × 도메인
- ✅ Fake/Mockito 활용한 단위 테스트

---

## 10. 파일 변경 인덱스

### 신규 생성
- `domain/common/Money.java`
- `domain/common/Quantity.java`
- `domain/product/ProductWithBrand.java`
- `domain/product/ProductDetailService.java`
- `test/.../common/MoneyTest.java`
- `test/.../common/QuantityTest.java`
- `test/.../brand/BrandModelTest.java`
- `test/.../product/ProductModelTest.java`
- `test/.../product/ProductDetailServiceTest.java`
- `test/.../stock/StockModelTest.java`
- `test/.../order/OrderModelTest.java`
- `test/.../order/OrderItemModelTest.java`
- `test/.../like/LikeModelTest.java`
- `test/.../like/LikeServiceTest.java`

### 수정 (도메인)
- `domain/product/ProductModel.java` — Money 적용
- `domain/stock/StockModel.java` — Quantity 적용
- `domain/order/OrderModel.java` — Money 적용
- `domain/order/OrderItemModel.java` — Money/Quantity 적용
- `domain/payment/PaymentModel.java` — Money 적용
- `domain/like/LikeRepository.java` — count 메서드 추가
- `domain/like/LikeService.java` — count 메서드 추가
- `infrastructure/like/LikeJpaRepository.java` — GROUP BY 쿼리 추가
- `infrastructure/like/LikeRepositoryImpl.java` — count 메서드 구현

### 수정 (애플리케이션 / 인터페이스)
- `application/product/ProductFacade.java` — getProductDetail, Domain Service 위임, N+1 회피
- `application/product/ProductInfo.java` — likeCount 필드 추가
- `application/like/LikeFacade.java` — likeCount 포함, N+1 회피
- `interfaces/api/product/ProductV1Dto.java` — likeCount 응답
- `interfaces/api/product/ProductV1Controller.java` — getProductDetail로 메서드명 변경

### 수정 (문서)
- `CLAUDE.md` — 도메인/아키텍처 룰 섹션 신규
- `docs/week2/03-class-diagram.md` — VO + Domain Service + Facade 다이어그램
- `docs/week2/02-sequence-diagrams.md` — 상품 상세 조회 흐름 재작성
- `docs/week2/04-erd.md` — VO ↔ DB 매핑 메모

---

## 11. 다음 주차 예고 (Week 4)

이번 주 Order 도메인은 **결제 흐름 제외**한 상태. 다음 주는:
- 동시 주문 시 재고 음수 방지 (낙관 락 vs 비관 락)
- 결제 + 재고 차감 + 포인트 사용 정합성 (단일 트랜잭션 vs SAGA)
- 트랜잭션 실패 시 보상 로직

→ 트랜잭션·동시성·실패 복구 설계가 주제.

---

## 12. 면접/이력서 활용 포인트

이번 주 작업에서 면접·이력서에 쓸 만한 의사결정 포인트:

1. **N+1 회피 패턴 일관 적용** — 재고는 처음부터 IN 쿼리였는데 좋아요는 N+1이던 걸 발견·수정. 일관성에 대한 안목.
2. **Domain Service 도입의 트레이드오프** — 단순 호출 묶기와 도메인 협력의 경계. "도입할 가치가 있는가"를 의식적으로 판단.
3. **VO 하이브리드 적용** — 외부 호환성과 도메인 무결성의 절충. 모든 계층에 VO를 흘리는 대신 도메인 내부에 격리.
4. **메서드 네이밍이 의도를 드러내는가** — `getProduct` vs `getProductDetail`의 의미 차이. 단순 1건 조회가 아닌 어셈블 결과임을 이름으로 표현.
5. **방어 코드 vs 신뢰 경계** — `ProductWithBrand` null 검증 삭제. 호출자가 보장하는 영역에 방어 코드를 두지 않는 판단.

블로그 글감으로:
- "ProductFacade의 `getProduct`를 `getProductDetail`로 바꾼 이유 — 메서드명이 드러내야 할 것"
- "VO를 도입할 때, 어디까지 흘려야 하는가"
- "Domain Service와 Facade는 결국 같아 보이는데 — 그래도 분리하는 기준"
