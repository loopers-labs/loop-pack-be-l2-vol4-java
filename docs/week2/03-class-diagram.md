# 클래스 다이어그램

## 도메인 모델

```mermaid
classDiagram
    class Brand {
        Long id
        String name
        String description
        validateName()
    }

    class Product {
        Long id
        Long brandId
        String name
        int price
        int stock
        deductStock(quantity)
        hasEnoughStock(quantity)
    }

    class Like {
        Long id
        Long userId
        Long productId
    }

    class Order {
        Long id
        Long userId
        OrderStatus status
        int totalPrice
        List~OrderItem~ items
        complete()
        cancel()
    }

    class OrderItem {
        Long id
        Long productId
        String productName
        int productPrice
        int quantity
    }

    class OrderStatus {
        <<enumeration>>
        PENDING
        COMPLETED
        CANCELLED
    }

    Order --> OrderStatus
    Order "1" *-- "N" OrderItem
    Product --> Brand
```

---

## 레이어별 구조

```mermaid
classDiagram
    class ProductService {
        +getProducts(filter) List~Product~
        +getProduct(productId) Product
        +deductStock(productId, quantity)
    }

    class LikeService {
        +like(userId, productId)
        +unlike(userId, productId)
        +getLikedProducts(userId) List~Product~
    }

    class OrderService {
        +createOrder(userId, items) Order
        +getOrders(userId, startAt, endAt) List~Order~
        +getOrder(userId, orderId) Order
    }

    class ProductRepository {
        <<interface>>
        +findById(id) Optional~Product~
        +findAll(filter) List~Product~
        +save(product) Product
    }

    class LikeRepository {
        <<interface>>
        +existsByUserIdAndProductId(userId, productId) boolean
        +findByUserId(userId) List~Like~
        +save(like) Like
        +delete(like)
    }

    class OrderRepository {
        <<interface>>
        +save(order) Order
        +findByUserIdAndOrderedAtBetween(userId, start, end) List~Order~
        +findById(id) Optional~Order~
    }

    ProductService --> ProductRepository
    LikeService --> LikeRepository
    LikeService --> ProductRepository
    OrderService --> OrderRepository
    OrderService --> ProductRepository
```

---

## 설계 포인트

**Product.deductStock()**
재고 차감 로직을 Service가 아닌 Product 도메인 객체에 둔다.
재고 부족 시 예외를 던지는 책임도 Product이 갖는다.

```java
public void deductStock(int quantity) {
    if (this.stock < quantity) {
        throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
    }
    this.stock -= quantity;
}
```

**OrderItem 스냅샷**
OrderItem은 주문 시점의 상품명과 가격을 직접 보유한다.
Product와의 연관은 `productId` 참조로만 유지하고, 상품 정보 변경에 영향받지 않는다.

**Order와 OrderItem 관계**
Order가 OrderItem 목록을 소유한다 (Aggregate Root).
OrderItem은 Order 없이 독립적으로 존재할 수 없다.
