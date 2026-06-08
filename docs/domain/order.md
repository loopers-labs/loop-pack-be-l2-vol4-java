# Order 도메인 비즈니스 규칙

## 주문 생성 (UC-07)

### 입력 필드
- `loginId`: Header (X-Loopers-LoginId), null 불가
- `items`: List, null/empty 불가 (1개 이상)
  - `productId`: null 불가, 목록 내 중복 불가
  - `quantity`: 1 이상

### 비즈니스 규칙 (Entity)
- `OrderItem.quantity >= 1`
- `Order.totalAmount > 0`
- `OrderItemStatus`: `ORDERED` (초기값)

### 비즈니스 규칙 (ApplicationService 검증)
- items가 비어 있으면 → `BAD_REQUEST`
- 동일한 `productId` 중복 포함 시 → `BAD_REQUEST`
- 존재하지 않거나 삭제된 상품 포함 시 → `BAD_REQUEST` (NOT_FOUND가 아님)
- 재고 부족 상품이 하나라도 있으면 → `BAD_REQUEST`, 품절 상품 ID 목록 포함 (올-오어-낫싱)

### 유스케이스 흐름 (ApplicationService)
1. `MemberService.getMember(loginId)` — 회원 조회 (없으면 NOT_FOUND)
2. items 중복 productId 검증 → 있으면 `BAD_REQUEST`
3. `ProductRepository.findAllByIdIn(productIds)` — 상품 일괄 조회
4. 조회된 상품 수 ≠ 요청 상품 수 → `BAD_REQUEST` (없는/삭제된 상품)
5. 각 item에 대해 `StockRepository.deductStock(productId, quantity)` — 원자적 차감
   - affected rows = 0 → 품절 상품 ID 수집
6. 품절 상품이 하나라도 있으면 예외 발생 → 트랜잭션 전체 롤백 (차감 취소)
7. `Order.create(memberId, totalAmount)` — 주문 엔티티 생성 (totalAmount = Σ price × quantity)
8. `OrderRepository.save(order)` — 주문 저장
9. `OrderItemRepository.saveAll(orderItems)` — 주문 항목 저장 (orderId, productId, quantity, ORDERED)
10. `OrderItemSnapshotRepository.saveAll(snapshots)` — 스냅샷 저장 (주문 시점 productName, brandName, price)

### 트랜잭션 경계
- `@Transactional` 위치: `OrderApplicationService.createOrder()`
- 재고 차감 + 주문 저장 + 항목 저장 + 스냅샷 저장을 **하나의 트랜잭션**으로 묶음
- 재고 부족 예외 발생 → 전체 롤백 (차감된 재고도 복구)

### 접근 제어
- 회원만 가능 (`X-Loopers-LoginId` 헤더 필수)
- 헤더 없으면 → 401 Unauthorized (현재는 getMember에서 NOT_FOUND로 처리)

### 응답 필드
- `orderId`: Long (생성된 주문 ID만 반환)

### 재고 부족 응답 예시
```json
{
  "code": "BAD_REQUEST",
  "message": "재고가 부족한 상품이 있습니다.",
  "data": {
    "outOfStockProductIds": [1, 3]
  }
}
```

---

## 주문 목록 조회 (UC-08)

### 입력 필드
- `loginId`: Header (X-Loopers-LoginId), null 불가
- `startAt`: Query param, nullable (없으면 시작 제한 없음)
- `endAt`: Query param, nullable (없으면 종료 제한 없음)
- `page`: Query param, default 0
- `size`: Query param, default 20

### 유스케이스 흐름 (ApplicationService)
1. `MemberService.getMember(loginId)` — 회원 조회 (없으면 NOT_FOUND)
2. `OrderRepository.findAllByMemberId(memberId, startAt, endAt, pageable)` — 페이지네이션 목록 조회

### 트랜잭션 경계
- `@Transactional(readOnly = true)`

### 접근 제어
- 회원만 가능 (`X-Loopers-LoginId` 헤더 필수)

### 응답 필드 (각 주문)
- `orderId`: Long
- `totalAmount`: Long
- `orderedAt`: ZonedDateTime (createdAt)

---

## 주문 상세 조회 (UC-09)

### 입력 필드
- `loginId`: Header (X-Loopers-LoginId), null 불가
- `orderId`: PathVariable, null 불가

### 비즈니스 규칙 (ApplicationService)
- 존재하지 않는 orderId → NOT_FOUND
- 본인의 주문이 아닌 경우 → FORBIDDEN

### 유스케이스 흐름 (ApplicationService)
1. `MemberService.getMember(loginId)` — 회원 조회 (없으면 NOT_FOUND)
2. `OrderRepository.findById(orderId)` — 주문 조회 (없으면 NOT_FOUND)
3. `order.memberId != member.id` → FORBIDDEN
4. `OrderItemRepository.findAllByOrderId(orderId)` — 주문 항목 목록
5. `OrderItemSnapshotRepository.findAllByOrderItemIdIn(orderItemIds)` — 스냅샷 일괄 조회
6. item + snapshot 조합해 응답 생성

### 트랜잭션 경계
- `@Transactional(readOnly = true)`

### 접근 제어
- 회원만 가능 (`X-Loopers-LoginId` 헤더 필수)
- 타인 주문 접근 → 403 FORBIDDEN

### 응답 필드
- `orderId`: Long
- `totalAmount`: Long
- `orderedAt`: ZonedDateTime
- `items[]`:
  - `productName`: String (스냅샷)
  - `brandName`: String (스냅샷)
  - `price`: Long (스냅샷, 주문 당시 가격)
  - `quantity`: int
  - `status`: OrderItemStatus

---

## 신규 도메인 모델

### Order
- `memberId`: Long, NOT NULL
- `totalAmount`: Long, NOT NULL, > 0
- `orderedAt`: ZonedDateTime, NOT NULL (createdAt 재사용 또는 별도 필드)

### OrderItem
- `orderId`: Long, NOT NULL
- `productId`: Long, NOT NULL
- `quantity`: int, >= 1
- `status`: OrderItemStatus (ORDERED / CANCELLED)

### OrderItemSnapshot
- `orderItemId`: Long, NOT NULL (FK → order_items)
- `productName`: String, NOT NULL
- `brandName`: String, NOT NULL
- `price`: Long, NOT NULL

### OrderItemStatus
- `ORDERED` — 정상 주문됨
- `CANCELLED` — 주문 후 취소됨 (추후 확장)

---

## 신규 Repository 메서드

### StockRepository (추가)
- `deductStock(Long productId, int quantity): int` — 원자적 차감, affected rows 반환
  ```sql
  UPDATE stocks SET quantity = quantity - :quantity
  WHERE product_id = :productId AND quantity >= :quantity AND deleted_at IS NULL
  ```

### ProductRepository (추가)
- `findAllByIdIn(List<Long> productIds): List<Product>`
