# ADR-017: 타인의 주문 접근 — 404 반환 정책

- 날짜: 2026-05-22
- 상태: 승인됨

## 결정

`GET /api/v1/orders/{orderId}` 요청 시, 해당 주문이 인증된 사용자의 주문이 아닌 경우 `404 Not Found`를 반환한다. 검증은 `OrderService` 또는 `OrderFacade` 레이어에서 `OrderEntity.isOwnedBy(userId)`로 수행한다.

```java
// OrderFacade 또는 OrderService
OrderEntity order = orderService.getOrder(orderId); // 없으면 404
if (!order.isOwnedBy(authUserId)) {
    throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
}
```

## 근거

타인의 주문에 `403 Forbidden`을 반환하면 해당 `orderId`가 실제로 존재한다는 정보를 간접적으로 노출한다. `404 Not Found`를 반환하면 "본인의 주문 중 해당 ID가 없음"과 "해당 ID의 주문 자체가 없음"을 구분하지 않으므로, 타인의 주문 ID 존재 여부를 추측할 수 없다.

요구사항에 **"유저는 타 유저의 정보에 직접 접근할 수 없습니다"** 라고 명시되어 있다.

### 고려한 대안

#### Option 1. 403 Forbidden 반환

주문이 존재하지만 접근 권한이 없음을 명시하는 방식이다.

- **장점**: 에러 원인이 명확하다 (권한 부족).
- **단점**: 해당 orderId가 실제로 존재한다는 정보가 노출된다. 악의적인 사용자가 orderId를 순회하며 타인의 주문 존재 여부를 확인할 수 있다.

---

#### Option 2. 404 Not Found 반환 (채택)

주문이 존재하더라도 본인 소유가 아니면 "없는 것"으로 처리하는 방식이다.

- **장점**: orderId 존재 여부를 노출하지 않아 보안상 안전하다. ADR-009(좋아요 목록 소유권 검증)와 일관된 정책이다.
- **단점**: 에러 원인이 "없음"으로 통일되어 디버깅 시 혼동 가능성이 있다 (내부 로그로 보완).

## 에러 응답

| 상황 | HTTP |
|---|---|
| orderId가 존재하지 않음 | 404 Not Found |
| orderId가 존재하지만 본인 주문이 아님 | 404 Not Found |
