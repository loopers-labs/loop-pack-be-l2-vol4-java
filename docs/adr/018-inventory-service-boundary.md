# ADR-018: InventoryService 도입 — 재고 차감 책임 분리

- 날짜: 2026-05-28
- 상태: 승인됨

## 결정

주문 생성 시 재고 차감은 `ProductService`가 아닌 `InventoryService`가 담당한다.
`OrderFacade`는 `InventoryService.deductAll(productId-quantity 쌍)`을 직접 호출한다.

## 근거

**도메인 경계 준수**: `ProductService`가 `InventoryRepository`에 직접 접근하면 Inventory 도메인 경계가 무너진다. 재고는 독립된 도메인(`domain/inventory/`)으로 분리되었으므로, 재고 관련 비즈니스 로직(정렬, 락 획득, `deduct()` 호출)은 `InventoryService`가 캡슐화해야 한다.

**단일 책임 원칙**: `ProductService`의 책임은 상품 정보 관리에 집중한다. 재고 차감 로직(FOR UPDATE 락, productId 오름차순 정렬, 재고 부족 예외)을 `ProductService`에 두면 책임이 과도하게 확장된다.

**테스트 용이성**: `InventoryService`로 분리하면 재고 차감 로직을 독립적으로 테스트할 수 있다.

## 흐름

```
OrderFacade.createOrder()
  ├── ProductService.getProducts(productIds)         # 상품 조회 (fast fail용)
  ├── OrderService.createOrder(userId, items)        # 주문 생성
  └── InventoryService.deductAll(productId-quantity) # 재고 차감 (FOR UPDATE)
```

## 트레이드오프

- `OrderFacade`가 `ProductService`와 `InventoryService` 두 서비스를 모두 의존한다.
- 주문 생성과 재고 차감을 하나의 트랜잭션으로 묶어야 하므로 `OrderFacade`에서 `@Transactional` 경계를 가진다 (ADR-012 예외 케이스).
