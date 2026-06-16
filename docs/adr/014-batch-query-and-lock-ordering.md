# ADR-014: 재고 차감 락 순서 정렬 — IN FOR UPDATE + Service 정렬

- 날짜: 2026-05-22
- 상태: 승인됨

## 결정

재고 차감 시 데드락 가능성을 낮추기 위해 Service 레이어에서 `productId` 오름차순 정렬 후 `IN (...) ORDER BY product_id FOR UPDATE`를 실행한다.

## 근거

### 재고 차감 — IN FOR UPDATE + Service 정렬

재고 차감은 `SELECT ... FOR UPDATE`로 락을 걸어야 한다. 이때 데드락 가능성을 낮추기 위해 두 가지 원칙을 함께 적용한다.

**원칙 1. IN FOR UPDATE + ORDER BY**

`WHERE product_id IN (...) ORDER BY product_id FOR UPDATE`를 사용하면 일반적으로 index 순서 기반으로 락을 획득한다. `IN (...)` 조회 결과의 반환 순서는 DB가 보장하지 않을 수 있으므로, `ORDER BY product_id`를 명시하여 lock ordering 의도를 SQL 레벨에서도 표현한다.

> MySQL InnoDB는 optimizer 판단, execution plan, index 선택, secondary index 사용 여부 등에 따라 실제 lock acquisition order가 달라질 수 있다. "항상 PK 오름차순으로 락을 획득한다"고 단정할 수 없으므로 `ORDER BY`로 의도를 명시한다.

**원칙 2. Service 레이어에서 productId 정렬 (방어적 보완)**

DB 레벨의 동작에만 의존하지 않고, Service 레이어에서도 명시적으로 `productId` 오름차순 정렬 후 IN 쿼리를 실행한다. 모든 트랜잭션이 가능한 한 동일한 lock ordering을 따르도록 유도한다.

```java
// InventoryService
public void deductInventories(Map<Long, Integer> productQuantityMap) {
    List<Long> sortedProductIds = productQuantityMap.keySet()
        .stream().sorted().toList(); // productId 오름차순 정렬

    List<InventoryEntity> inventories =
        inventoryRepository.findAllByProductIdsForUpdate(sortedProductIds);
    // SELECT * FROM product_inventory
    // WHERE product_id IN (...)
    // ORDER BY product_id
    // FOR UPDATE

    inventories.forEach(inv ->
        inv.deduct(productQuantityMap.get(inv.getProductId()))
    );
}
```

### 고려한 대안

#### Option 1. 루프 + 개별 SELECT FOR UPDATE (기각)

상품마다 `SELECT * FROM product_inventory WHERE product_id = ? FOR UPDATE`를 실행하는 방식이다.

- **장점**: 구현이 단순하다.
- **단점**: N번의 DB round-trip이 발생한다. 루프 순서가 호출부에 따라 달라지면 데드락 위험이 높아진다.

---

#### Option 2. 정렬된 루프 + 개별 SELECT FOR UPDATE (기각)

productId 오름차순으로 정렬 후 루프를 도는 방식이다.

- **장점**: 락 순서를 일관되게 유지할 수 있어, 대표적인 순환 대기 기반 데드락 가능성을 낮춘다.
- **단점**: N번의 DB round-trip은 여전히 발생한다.

---

#### Option 3. IN FOR UPDATE + Service 정렬 (채택)

`WHERE product_id IN (...) ORDER BY product_id FOR UPDATE`로 한 번에 락을 잡고, Service에서 정렬로 방어하는 방식이다.

- **장점**: DB round-trip 1회. SQL ORDER BY + 애플리케이션 정렬로 이중 방어.
- **단점**: InnoDB의 lock acquisition 동작에 대한 이해가 필요하다.

## 데드락 방지 원리

데드락은 **순환 대기**가 발생할 때 일어난다. 동일한 lock ordering 전략은 순환 대기 가능성을 크게 낮추며, 재고 차감 시 발생 가능한 대표적인 데드락 패턴을 예방한다.

```
트랜잭션 A: product_id IN (1, 2) → 1 락 → 2 락
트랜잭션 B: product_id IN (2, 3) → 2 대기 → A 완료 후 2, 3 락

대표적인 순환 대기 패턴 예방:
B가 2를 갖고 A가 1을 기다리는 순환이 발생하려면
B가 먼저 2를 잡아야 하는데, 정렬 전략 하에서 B는 가장 작은 ID부터 시도하므로 가능성이 크게 줄어든다.
```

> InnoDB에서는 lock ordering 전략을 적용하더라도 secondary index lock, gap lock / next-key lock, Hibernate flush timing 등으로 인해 데드락이 완전히 제거되지는 않는다. 따라서 애플리케이션은 데드락 발생 시 재시도 가능하도록 설계하는 것을 권장한다 (e.g. `DeadlockLoserDataAccessException` 감지 후 retry).
