# ADR-001: OrderItem 스냅샷 패턴

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

`OrderItemModel`에 주문 시점의 상품 정보(`productName`, `productPrice`)를 스냅샷 컬럼으로 저장한다. `Product`와의 JPA `@ManyToOne` 관계는 사용하지 않는다.

## 근거

요구사항 문서에 **"주문 정보에는 당시의 상품 정보가 스냅샷으로 저장되어야 합니다"** 라고 명시되어 있다.

주문 이후 상품 가격 변경 또는 상품 삭제(soft delete)가 발생하더라도, 주문 이력은 주문 당시의 정보를 그대로 유지해야 한다. `@ManyToOne`을 사용하면 라이브 데이터와 스냅샷 데이터가 혼재해 실수로 현재 가격을 읽는 버그가 발생할 수 있다.

## 참고

- Martin Fowler — Snapshot Pattern: https://martinfowler.com/eaaDev/Snapshot.html
- DDD Aggregate Root 원칙: Order가 Aggregate Root이며, Product는 외부 Aggregate이므로 ID 참조만 권장
- BroadleafCommerce 등 실 이커머스 플랫폼에서 동일 패턴 사용
