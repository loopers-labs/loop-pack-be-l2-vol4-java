# ADR-014: 배치 IN 쿼리 + 재고 차감 락 순서 정렬

- 날짜: 2026-05-22
- 상태: 승인됨

## 결정

여러 상품을 대상으로 하는 조회·삭제·재고 차감 작업은 루프 대신 **배치 IN 쿼리**로 처리한다. 재고 차감 시에는 Service 레이어에서 `productId` 오름차순 정렬 후 `IN (...) FOR UPDATE`를 실행하여 데드락을 방어한다.

| 작업 | 방식 |
|---|---|
| 주문 생성 - 상품 조회 | `WHERE id IN (productIds)` 1회 |
| 브랜드 삭제 - 연쇄 삭제 | `WHERE product_id IN (productIds)` 일괄 soft delete |
| 주문 생성 - 재고 차감 | productId 오름차순 정렬 후 `WHERE product_id IN (...) FOR UPDATE` |

## 근거

### 배치 IN 쿼리 채택 이유

루프 방식은 상품 N개에 대해 DB 쿼리를 N번 발생시킨다. N+1 문제와 동일한 구조로, 상품 수가 늘어날수록 불필요한 DB round-trip이 증가한다. `IN (...)` 쿼리로 1번에 처리하면 동일한 결과를 얻으면서 성능을 개선할 수 있다.

### 재고 차감 — IN FOR UPDATE + Service 정렬

재고 차감은 `SELECT ... FOR UPDATE`로 락을 걸어야 한다. 이때 데드락을 방지하기 위해 두 가지 원칙을 함께 적용한다.

**원칙 1. IN FOR UPDATE**

`WHERE product_id IN (...) FOR UPDATE`를 사용하면 MySQL InnoDB가 PK 오름차순으로 행 락을 획득한다. 모든 트랜잭션이 동일한 순서로 락을 잡으므로 순환 대기(circular wait)가 발생하지 않는다.

**원칙 2. Service 레이어에서 productId 정렬 (방어적 보완)**

MySQL 내부 동작에만 의존하지 않고, Service 레이어에서 명시적으로 `productId` 오름차순 정렬 후 IN 쿼리를 실행한다. DB 엔진의 동작 방식이 변경되거나 다른 환경에서도 동일한 락 순서를 보장한다.

```java
// ProductInventoryService
public void deductInventories(Map<Long, Integer> productQuantityMap) {
    List<Long> sortedProductIds = productQuantityMap.keySet()
        .stream().sorted().toList(); // productId 오름차순 정렬

    List<ProductInventoryModel> inventories =
        inventoryRepository.findAllByProductIdsForUpdate(sortedProductIds);
    // SELECT * FROM product_inventory WHERE product_id IN (...) FOR UPDATE

    inventories.forEach(inv ->
        inv.deduct(productQuantityMap.get(inv.getProductId()))
    );
}
```

### 고려한 대안

#### Option 1. 루프 + 개별 SELECT FOR UPDATE (기각)

상품마다 `SELECT * FROM product_inventory WHERE product_id = ? FOR UPDATE`를 실행하는 방식이다.

- **장점**: 구현이 단순하다.
- **단점**: N번의 DB round-trip이 발생한다. 루프 순서가 호출부에 따라 달라지면 데드락 위험이 생긴다.

---

#### Option 2. 정렬된 루프 + 개별 SELECT FOR UPDATE (기각)

productId 오름차순으로 정렬 후 루프를 도는 방식이다.

- **장점**: 락 순서가 보장되어 데드락이 없다.
- **단점**: N번의 DB round-trip은 여전히 발생한다.

---

#### Option 3. IN FOR UPDATE + Service 정렬 (채택)

`WHERE product_id IN (...) FOR UPDATE`로 한 번에 락을 잡고, Service에서 정렬로 방어하는 방식이다.

- **장점**: DB round-trip 1회. MySQL의 PK 순서 락 + 애플리케이션 정렬로 이중 방어.
- **단점**: MySQL 내부 동작(PK 순서 락)에 대한 이해가 필요하다.

## 데드락 방지 원리

데드락은 **순환 대기**가 발생할 때 일어난다. 모든 트랜잭션이 동일한 순서로 락을 획득하면 순환 대기가 구조적으로 불가능하다.

```
트랜잭션 A: product_id IN (1, 2) FOR UPDATE → 1 락 → 2 락
트랜잭션 B: product_id IN (2, 3) FOR UPDATE → 1... 아니, 2 대기 → A 완료 후 2, 3 락

순환 없음: B가 2를 갖고 A가 기다리는 상황이 발생하려면
B가 먼저 2를 잡아야 하는데, B는 1부터 잡아야 하고 1은 A가 갖고 있으므로 불가능.
```
