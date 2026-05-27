# Plan: ORD-6 관리자 주문 상세

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

관리자가 `GET /api-admin/v1/orders/{orderId}`로 임의 주문의 상세를 조회한다. `/api-admin/**`는 `AdminAuthInterceptor`가 자동 가드(403). `OrderFacade.readOrder(orderId)`가 `orderRepository.getActiveById(orderId)`(미존재 시 NOT_FOUND)로 주문을 조회하고, `findActiveItemsByOrderId`로 항목을 별도 조회해, 소유 검증 없이 회원 식별자를 포함한 항목 전체를 `OrderAdminInfo`로 매핑한다. ORD-4(본인 상세)와 달리 소유 필터가 없고 응답에 `userId`가 포함된다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검

- [x] 호출 방향 준수 — admin Facade·Repository 조회 메서드 추가
- [x] 인증: `AdminAuthInterceptor`가 `/api-admin/**` 가드(403). 컨트롤러에 인증 파라미터 없음
- [x] 결정 5(스냅샷): 항목은 `OrderItemModel` 스냅샷 그대로
- [x] 미존재 → NOT_FOUND. 소유 검증 없음(관리자는 모든 주문 조회)
- [x] 조회 트랜잭션: `@Transactional(readOnly=true)`. 항목은 `findActiveItemsByOrderId`로 별도 조회

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/order/OrderAdminV1Controller.java` (편집) — `@GetMapping("/{orderId}")` `readOrder(@PathVariable Long orderId)`, 200. `orderFacade.readOrder(orderId)` 후 `ApiResponse.success(OrderAdminV1Dto.DetailResponse.from(info))`.
- `interfaces/api/order/OrderAdminV1Dto.java` (편집) — `DetailResponse(Long orderId, Long userId, String status, ZonedDateTime orderedAt, Integer totalPrice, List<ItemResponse> items)` + `from(OrderAdminInfo)`, `ItemResponse(productId, productName, brandName, unitPrice, quantity)` + `from(OrderItemInfo)`.
- `interfaces/api/order/OrderAdminV1ApiSpec.java` (편집) — 상세 `@Operation` 추가.

### application
- `application/order/OrderFacade.java` (편집) — `@Transactional(readOnly=true) OrderAdminInfo readOrder(Long orderId)`: `OrderModel order = orderRepository.getActiveById(orderId)` → `List<OrderItemModel> items = orderRepository.findActiveItemsByOrderId(order.getId())` → `OrderAdminInfo.from(order, items)`.
- `application/order/OrderAdminInfo.java` (신규) — `record(Long orderId, Long userId, OrderStatus status, ZonedDateTime orderedAt, Integer totalPrice, List<OrderItemInfo> items)` + `from(OrderModel order, List<OrderItemModel> items)` (ORD-1 `OrderItemInfo` 재사용).

### domain
- `domain/order/OrderRepository.java` (ORD-1에서 정의) — `OrderModel getActiveById(Long orderId)` (미존재 시 NOT_FOUND)·`findActiveItemsByOrderId`.

### infrastructure
- `infrastructure/order/OrderRepositoryImpl.java` (ORD-1) — `orderJpaRepository.findByIdAndDeletedAtIsNull(orderId).orElseThrow(NOT_FOUND)`; 항목은 `OrderItemJpaRepository.findByOrderIdAndDeletedAtIsNull`.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `OrderAdminInfo`를 ORD-4 `OrderInfo`와 별도로 | admin 상세는 `userId`를 포함(본인 상세는 비포함). 응답 필드가 달라 분리가 명확 | `OrderInfo` 재사용 후 userId 끼워넣기(member 응답에 userId 누출 위험) |
| `getActiveById`는 소유 필터 없음 | 관리자는 모든 주문 조회(명세). 미존재(또는 삭제)만 404 | 소유 필터 추가(관리자 권한과 모순) |
