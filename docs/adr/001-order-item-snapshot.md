# ADR-001: OrderItem 스냅샷 패턴

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

`OrderItemEntity`에 주문 시점의 상품 정보(`productName`, `productPrice`)를 스냅샷 컬럼으로 저장한다. `Product`와의 JPA `@ManyToOne` 관계는 사용하지 않는다.

## 근거

요구사항 문서에 **"주문 정보에는 당시의 상품 정보가 스냅샷으로 저장되어야 합니다"** 라고 명시되어 있다. 주문 이후 상품 가격이 변경되거나 상품이 삭제(soft delete)되더라도, 주문 이력은 주문 당시의 정보를 그대로 유지해야 한다.

### 고려한 대안

#### Option 1. @ManyToOne 관계로 Product 참조

`OrderItemEntity`이 `ProductEntity`을 `@ManyToOne`으로 참조하여, 조회 시 JOIN으로 상품 정보를 가져오는 방식이다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "product_id")
private ProductEntity product;
```

- **장점**: 별도 스냅샷 컬럼 없이 상품 정보를 재사용할 수 있다. 스키마가 단순하다.
- **단점**: 주문 이후 상품 가격이 변경되면 주문 이력에서도 변경된 가격이 조회된다. 상품이 삭제(soft delete)되면 조회 결과가 달라질 수 있다. 실수로 현재 가격을 주문 당시 가격으로 오해하는 버그가 발생하기 쉽다. DDD 원칙상 Order Aggregate와 Product Aggregate는 서로 다른 Aggregate Root이므로 직접 참조보다 ID 참조를 권장한다.

---

#### Option 2. 스냅샷 컬럼 저장 (채택)

주문 시점의 상품 정보를 `OrderItemEntity`의 컬럼에 직접 복사하여 저장하는 방식이다.

```java
@Column(name = "product_name")
private String productName;   // 주문 시점 상품명

@Column(name = "product_price")
private Long productPrice;    // 주문 시점 가격
```

- **장점**: 주문 이후 상품 정보가 변경되거나 삭제되어도 주문 이력은 항상 당시 데이터를 보존한다. Order Aggregate가 외부 Aggregate에 의존하지 않아 독립성이 높다. BroadleafCommerce 등 실 이커머스 플랫폼에서 동일 패턴을 사용한다.
- **단점**: 상품명·가격 변경이 기존 주문에 반영되지 않는다. (이는 요구사항상 의도된 동작이다.) 데이터가 중복 저장된다.

## 스냅샷 범위 결정 — brandName / brandId 미포함

현재 스냅샷 컬럼은 `productName`, `productPrice`만 포함한다. `brandName`과 `brandId`는 포함하지 않는다.

**근거**: 주문 이력에서 핵심적으로 보존해야 하는 정보는 "어떤 상품을, 얼마에 샀는가"이다. 브랜드는 상품의 속성이지만, 주문 시점의 브랜드명이 이후 변경되더라도 법적·정산적 의미에서 문제가 되지 않는다. 또한 `productId`를 스냅샷에 유지하고 있으므로, 브랜드 정보가 필요한 경우 `productId`로 Product를 조회해 현재 브랜드를 참조할 수 있다.

## 향후 고려사항

향후 브랜드명, 상품 이미지 등 추가 정보가 필요해질 경우 스냅샷 컬럼을 확장한다.
