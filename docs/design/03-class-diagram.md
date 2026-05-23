# 클래스 다이어그램

## 도메인 모델 관계도

```mermaid
classDiagram
  class BaseEntity {
    <<abstract>>
    +Long id
    +LocalDateTime createdAt
    +LocalDateTime updatedAt
    +LocalDateTime deletedAt
    +delete()
    +restore()
    #guard()
  }

  class User {
    +String loginId
    +String loginPassword
    +String name
    +LocalDate birthday
    +String email
  }

  class Brand {
    +String name
    +String description
  }

  class Product {
    +Long brandId
    +String name
    +BigDecimal price
  }

  class ProductStock {
    +Long productId
    +long quantity
    +deduct(long quantity)
  }

  class ProductLike {
    +Long userId
    +Long productId
    +LocalDateTime createdAt
  }

  class Order {
    +Long userId
    +OrderStatus status
    +int totalPrice
    +confirm()
    +cancel()
  }

  class OrderItem {
    +Long productId
    +String productName
    +int productPrice
    +int quantity
    +int subtotalPrice
  }

  BaseEntity <|-- User
  BaseEntity <|-- Brand
  BaseEntity <|-- Product
  BaseEntity <|-- Order
  BaseEntity <|-- OrderItem

  Brand "1" --> "N" Product : 소속
  Product "1" *-- "1" ProductStock : 재고
  User "1" --> "N" ProductLike : 좋아요
  Product "1" --> "N" ProductLike : 좋아요
  User "1" --> "N" Order : 주문
  Order "1" *-- "N" OrderItem : 주문 상품
    end
```

---

## 도메인 객체 설명

### Brand & Product

  - `Brand` 브랜드 정보 보유. 삭제 시 소속 `Product` 전체가 함께 Soft Delete
- `Product`는 `brandId`로 `Brand`를 참조. 직접 객체 참조 대신 ID 참조 사용

---

### ProductStock

`Product`와 1:1로 대응되는 재고 모델. 재고는 주문마다 빈번하게 갱신되므로 `ProductStock`에만 비관적 락을 적용해 락 범위를 최소화

- `deduct(quantity)` — 재고 차감. 차감 후 수량이 0 미만이면 `CoreException`을 던짐

---

### ProductLike

유저 - 상품 좋아요 관계 표현.

- 등록 시 이미 존재하면 무시, 취소 시 존재하지 않으면 무시 — **멱등성**은 `LikeService`에서 존재 여부를 확인 후 분기 처리

---

### Order & OrderItem

- `Order`는 주문 단위. `status`는 `PENDING → CONFIRMED → CANCELED` 흐름
- `OrderItem`은 주문 시점의 상품명·가격을 스냅샷으로 저장. `Product` 정보가 변경되어도 주문 내역은 영향받지 않음.

---

### OutboxEvent

주문 완료 후 외부 시스템 연동을 위한 트랜잭션 아웃박스. 주문 생성 트랜잭션 내에서 함께 저장되어 이벤트 유실을 방지.

- `PENDING` 상태의 이벤트를 폴링해 처리 후 상태를 `PROCESSED`로 전환.
