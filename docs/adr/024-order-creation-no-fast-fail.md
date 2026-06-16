# ADR-024: 주문 생성 흐름 — fast-fail 사전 재고 검증 미적용

- 날짜: 2026-05-29
- 상태: 승인됨

## 결정

주문 생성 시 트랜잭션 외부에서 재고를 사전 검증하는 **fast-fail 단계를 적용하지 않는다**.

재고 검증은 `inventoryService.deductAll()` 내부의 `SELECT FOR UPDATE` 단일 지점에서만 수행한다.

## 배경

`01-requirements.md` 초안의 주문 생성 플로우는 2단계 재고 검증을 제안했다.

```
상품 조회 → [재고 사전 확인, 락 없음] → @Transactional 시작 → 주문 생성 → [재고 차감, SELECT FOR UPDATE]
```

- **1단계**: 트랜잭션 밖에서 락 없이 재고 확인 → 명백한 부족 시 조기 400 반환
- **2단계**: 트랜잭션 안에서 `SELECT FOR UPDATE` 로 재고 차감 → 실제 동시성 보장

## 미적용 근거

### 1. 재고 확인 이중화

fast-fail을 추가하면 하나의 주문 요청에서 재고 조회가 두 번 발생한다.

```
[사전 확인] inventoryRepository.findAllByProductIds()    ← 락 없는 SELECT
[실제 차감] inventoryRepository.findAllByProductIdsWithLock()  ← SELECT FOR UPDATE
```

두 번째 조회에서 어차피 재고를 재확인하므로 첫 번째 조회는 중복이다.

### 2. Facade에 도메인 로직 유입

사전 재고 검증 로직을 추가하려면 `OrderFacade`에서 재고 부족 여부를 판단해야 한다.

```java
// 만약 fast-fail을 구현한다면
public OrderInfo createOrder(Long userId, List<OrderItemCommand> commands) {
    // ...상품 조회...

    // ← Facade에 재고 검증 로직 유입
    commands.forEach(cmd -> {
        InventoryEntity inv = inventoryService.getByProductId(cmd.productId());
        if (inv.getQuantity() < cmd.quantity()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
    });

    // ...주문 생성 및 실제 차감...
}
```

재고 충분 여부 판단은 **도메인 규칙**으로, `InventoryEntity.deduct()` 에 이미 캡슐화되어 있다. 이를 Facade에서 중복 판단하면 도메인 로직이 Application Layer로 누출된다.

### 3. 락 없는 사전 확인의 한계

fast-fail 단계는 락이 없으므로 동시 요청에서 **False Negative**가 발생할 수 있다.

```
Thread A: 사전 확인 → 재고 5개 남음 → 통과
Thread B: 사전 확인 → 재고 5개 남음 → 통과
Thread A: SELECT FOR UPDATE → 차감 → 재고 0
Thread B: SELECT FOR UPDATE → 재고 부족 → Rollback
```

결국 정확한 재고 보호는 `SELECT FOR UPDATE` 에서만 가능하다. fast-fail은 "명백한 부족"을 조기 차단하는 UX 최적화일 뿐, 정합성 보장 역할은 없다.

## 채택된 흐름

```
OrderFacade.createOrder() [@Transactional]
  1. productService.getProduct() ×N  → 상품 존재 확인 (없으면 404)
  2. orderService.createOrder()      → 주문 + OrderItem 스냅샷 INSERT
  3. inventoryService.deductAll()    → SELECT FOR UPDATE → deduct()
                                        재고 부족 시 CoreException → Rollback → 400
```

재고 검증은 `InventoryEntity.deduct()` 한 곳에서만 수행되며, 동시성은 `SELECT FOR UPDATE` 락이 보장한다.

## 트레이드오프

| 항목 | fast-fail 적용 | 미적용 (현재) |
|---|---|---|
| 재고 조회 횟수 | 2회 | 1회 |
| 도메인 로직 위치 | Facade + Domain | Domain만 |
| 명백한 부족 조기 차단 | ✅ | ❌ |
| 정합성 보장 | SELECT FOR UPDATE | SELECT FOR UPDATE |

정합성 보장 주체가 동일하고, 도메인 경계 유지와 코드 단순성을 우선하여 미적용을 선택한다.
