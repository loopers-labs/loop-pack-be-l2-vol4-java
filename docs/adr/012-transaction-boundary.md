# ADR-012: 트랜잭션 경계 원칙

- 날짜: 2026-05-21
- 상태: 승인됨

## 결정

트랜잭션 경계는 **Service** 레이어에서 시작한다. Facade는 기본적으로 `@Transactional`을 적용하지 않는다. 단, 여러 Service의 쓰기 작업을 원자적으로 묶어야 하는 경우에 한해 Facade에 `@Transactional`을 적용한다.

| 레이어 | 규칙 |
|---|---|
| Controller | `@Transactional` 미적용 |
| Facade | 기본적으로 미적용. 원자성이 필요한 경우에 한해 적용 |
| Service (조회) | `@Transactional(readOnly = true)` |
| Service (쓰기) | `@Transactional` |

## 근거

트랜잭션은 DB 자원을 점유하므로 가능한 한 짧고 좁게 유지해야 한다. Controller나 Facade까지 트랜잭션을 열어두면 불필요하게 Connection이 장시간 점유되어 성능 저하가 발생한다. 따라서 DB에 직접 접근하는 Service 레이어에서 트랜잭션을 열고 닫는 것이 원칙이다.

### 고려한 대안

#### Option 1. Service에서 타 Service를 의존성 주입받아 처리

주문 생성과 재고 차감을 하나의 Service 메서드 안에서 처리하기 위해, `OrderService`가 `InventoryService`를 주입받는 방식이다.

```java
// OrderService
@Transactional
public OrderEntity createOrder(...) {
    OrderEntity order = orderRepository.save(...);
    inventoryService.deduct(...); // ← Service 간 직접 호출
    return order;
}
```

- **장점**: Facade에 트랜잭션이 없어도 된다. 트랜잭션 경계가 Service 레이어에 유지된다.
- **단점**: Service 간 직접 의존성 주입은 DDD 원칙에 어긋난다. 도메인 경계를 넘는 결합이 생겨 응집도가 낮아지고, 변경 시 영향 범위가 커진다.

---

#### Option 2. Facade에서 @Transactional로 원자적 처리 (채택)

Facade가 트랜잭션 경계를 갖고, 복수의 Service를 조합하여 원자적으로 처리하는 방식이다.

```java
// OrderFacade
@Transactional
public OrderInfo createOrder(...) {
    ProductEntity product = productService.getProduct(...); // 트랜잭션 참여
    // 재고 fast fail 검증
    OrderEntity order = orderService.createOrder(product, ...);       // 주문 생성
    inventoryService.deductInventories(product.getId(), quantity);   // 재고 차감 (FOR UPDATE)
    return OrderInfo.from(order);
}
```

- **장점**: Service 간 직접 결합 없이 DDD 원칙을 유지한다. Facade가 오케스트레이션 책임을 갖는 기존 설계와 일관성이 있다.
- **단점**: "Facade는 트랜잭션에 관여하지 않는다"는 원칙에 예외가 생긴다. 남용될 경우 Facade가 비대해질 수 있다.

## 적용 범위

현재 Facade 레이어에서 `@Transactional`이 필요한 케이스는 `OrderFacade.createOrder()` 하나이다. 그 외 Facade는 모두 단일 Service 호출로 처리되므로 `@Transactional` 미적용 원칙을 따른다.
