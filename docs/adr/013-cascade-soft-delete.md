# ADR-013: 연쇄 삭제 정책 — Facade 오케스트레이션 + Like 연쇄 Soft Delete

- 날짜: 2026-05-21
- 상태: 승인됨

## 결정

Brand/Product 삭제 시 연관 엔티티(Inventory, Like)를 Facade 오케스트레이션으로 연쇄 Soft Delete한다. JPA Cascade는 사용하지 않는다.

```
Brand 삭제
  └── Product 연쇄 soft delete → Inventory soft delete + Like soft delete

Product 삭제
  └── Inventory soft delete + Like soft delete

Like 삭제 (좋아요 취소)
  └── 단독 삭제, 연쇄 없음
```

## 근거

### JPA Cascade를 사용하지 않는 이유

Like는 `productId`(Long) ID 참조 방식으로 설계되어 있어 `ProductEntity`와 JPA 관계가 없다. Like와 Product는 서로 다른 Aggregate이며, 다대다 관계이므로 ID 참조를 유지하는 것이 DDD 원칙에 부합한다. JPA Cascade를 적용하려면 JPA 관계를 추가해야 하는데, 이는 Aggregate 경계를 위반하고 N+1 문제 위험을 초래한다.

### Like 연쇄 Soft Delete를 선택한 이유

삭제된 상품에 대한 좋아요는 사용자에게 의미 없는 데이터다. 내 좋아요 목록 조회 시 삭제된 상품을 참조하는 Like를 필터링하는 별도 처리가 필요해지므로, 연쇄 삭제로 구조를 단순하게 유지한다.

### 고려한 대안

#### Option 1. JPA Cascade로 자동 처리 (기각)

`@OneToMany(cascade = CascadeType.ALL)` 설정으로 Product 삭제 시 연관 엔티티를 자동 삭제하는 방식이다.

- **장점**: 코드가 줄고 삭제 누락 위험이 없다.
- **단점**: Like에 JPA 관계 추가가 필요하다. Like와 Product는 다른 Aggregate이므로 ID 참조를 유지해야 하며, JPA 관계 추가는 Aggregate 경계 위반이다. N+1 문제 위험도 생긴다.

---

#### Option 2. Facade 오케스트레이션 (채택)

Facade가 각 Service를 순서대로 호출해 명시적으로 연쇄 삭제를 처리하는 방식이다.

```java
// ProductFacade
public void deleteProduct(Long productId) {
    productService.delete(productId);
    inventoryService.deleteByProduct(productId);
    likeService.deleteAllByProduct(productId);
}
```

- **장점**: JPA 관계 추가 없이 Aggregate 경계를 유지한다. 삭제 순서와 범위가 명시적으로 드러나 가독성이 높다. 향후 삭제 정책이 변경될 때 Facade만 수정하면 된다.
- **단점**: Facade에 삭제 오케스트레이션 코드가 추가된다. 삭제 대상이 많을 경우 (브랜드 삭제 시 상품이 많은 경우) 순차 처리로 인한 성능 이슈가 발생할 수 있다.

---

#### Option 3. Like 유지 (기각)

Brand/Product 삭제 시 Like를 삭제하지 않고 이력으로 보존하는 방식이다.

- **장점**: 좋아요 이력이 보존된다.
- **단점**: 내 좋아요 목록 조회 시 삭제된 상품을 참조하는 Like를 필터링하는 처리가 필요하다. 조회 로직이 복잡해진다.

## like_count 처리 원칙

- 좋아요 취소(`removeLike`) 시에는 `like_count`를 차감한다.
- Brand/Product 삭제로 인한 Like 연쇄 삭제 시에는 `like_count` 차감을 수행하지 않는다. 상품 자체가 삭제되므로 `like_count` 정합성 유지가 불필요하다.

## 향후 고려사항

Brand 삭제 시 연관 상품이 대량일 경우, 순차 처리 대신 `productIds`를 한 번에 조회하여 batch soft delete하는 방식으로 최적화할 수 있다.
