# Plan: ORD-4 본인 주문 상세

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

회원이 `GET /api/v1/orders/{orderId}`로 본인 주문 상세를 조회한다. `OrderFacade.readMyOrder(authUserId, orderId)`가 `orderRepository.getActiveByIdAndUserId(orderId, authUserId)`로 조회 — 미존재든 타인 소유든 활성 행이 없으면 RepositoryImpl이 NOT_FOUND를 던져 enumeration을 막는다(단일 쿼리로 두 경우 통합, get* 컨벤션). 항목은 `orderRepository.findActiveItemsByOrderId(orderId)`로 별도 조회해 ORD-1의 `OrderInfo.from(order, items)`로 반환한다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검

- [x] 호출 방향 준수 — `OrderFacade` 조회 메서드 추가, `OrderRepository` 조회 메서드 추가
- [x] 인증: `@LoginUser AuthenticatedUser` → 실패 401
- [x] 보안(비기능): 미존재·타인 소유 모두 404 — `getActiveByIdAndUserId`가 활성 행 없으면 NOT_FOUND (403 아님)
- [x] 결정 5(스냅샷): 항목은 `OrderItemModel` 스냅샷 그대로 반환 (ORD-1 `OrderInfo`/`OrderItemInfo` 재사용)
- [x] 조회 트랜잭션: `@Transactional(readOnly=true)`. 항목은 `findActiveItemsByOrderId`로 별도 조회

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/order/OrderV1Controller.java` (편집) — `@GetMapping("/{orderId}")` `readMyOrder(@PathVariable Long orderId, @LoginUser AuthenticatedUser loginUser)`, 200. `orderFacade.readMyOrder(loginUser.userId(), orderId)` 후 `ApiResponse.success(OrderV1Dto.OrderResponse.from(info))`.
- `interfaces/api/order/OrderV1Dto.java` (편집 없음 또는 재사용) — 상세 응답은 ORD-1 `OrderResponse`(orderId·status·orderedAt·totalPrice·items)와 동일 필드라 그대로 사용.
- `interfaces/api/order/OrderV1ApiSpec.java` (편집) — 상세 조회 `@Operation` 추가.

### application
- `application/order/OrderFacade.java` (편집) — `@Transactional(readOnly=true) OrderInfo readMyOrder(Long authUserId, Long orderId)`: `OrderModel order = orderRepository.getActiveByIdAndUserId(orderId, authUserId)` (없으면 Impl NOT_FOUND) → `List<OrderItemModel> items = orderRepository.findActiveItemsByOrderId(order.getId())` → `OrderInfo.from(order, items)`.
- `OrderInfo`/`OrderItemInfo` (ORD-1) 재사용.

### domain
- `domain/order/OrderRepository.java` (ORD-1에서 정의) — `getActiveByIdAndUserId(orderId, userId)`·`findActiveItemsByOrderId(orderId)` 사용.

### infrastructure
- `infrastructure/order/OrderJpaRepository.java` (ORD-1) — `findByIdAndUserIdAndDeletedAtIsNull(id, userId)` 파생 쿼리.
- `infrastructure/order/OrderRepositoryImpl.java` (ORD-1) — `getActiveByIdAndUserId`는 `orElseThrow(NOT_FOUND)`; 항목은 `OrderItemJpaRepository.findByOrderIdAndDeletedAtIsNull` 위임.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 미존재·타인 소유를 `getActiveByIdAndUserId` 단일 쿼리로 통합해 둘 다 404 (NOT_FOUND는 Impl 책임) | enumeration 방지(타인 주문 식별자 존재 추론 차단). 쿼리 필터가 인메모리 소유 분기보다 단순하고 누수 없음. 도메인 `isOwnedBy`는 미사용이 되어 도입하지 않음(analyze 결정). get* 컨벤션대로 orElseThrow는 RepositoryImpl | `findById` 후 인메모리 소유 분기(`isOwnedBy`) — 403/404 갈림 위험, 미사용 도메인 메서드 잔존 |
| 항목을 `findActiveItemsByOrderId`로 별도 조회 | 연관 끊음(A-1)에 따라 항목은 orderId로 명시 조회. 단건이라 추가 쿼리 1회로 충분 | `@OneToMany` JOIN FETCH(연관 유지 — A-1에서 기각) |
