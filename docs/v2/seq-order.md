# Order Sequence Diagrams

## POST /api/v1/orders

```mermaid
sequenceDiagram
    actor User
    participant Controller as OrderV1Controller
    participant Facade as OrderFacade
    participant ProductService
    participant OrderService
    participant DB

    User->>Controller: POST /api/v1/orders {items: [{productId, quantity}]}
    Controller->>Facade: createOrder(userId, items)
    alt 동일 productId 중복
        Facade-->>Controller: CoreException(BAD_REQUEST)
        Controller-->>User: 400 Bad Request
    end
    Facade->>ProductService: getProducts(productIds)
    ProductService->>DB: SELECT * FROM products WHERE id IN (...) AND deleted_at IS NULL
    alt 존재하지 않는 상품 포함
        ProductService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>User: 404 Not Found
    end
    note over Facade, DB: @Transactional 시작
    Facade->>OrderService: createOrder(userId, snapshotItems)
    OrderService->>DB: INSERT INTO orders (user_id, status = ORDERED)
    OrderService->>DB: INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity)
    loop 각 OrderItem
        Facade->>ProductService: decreaseStock(productId, quantity)
        ProductService->>DB: UPDATE product_stocks SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?
        alt affected rows = 0 (재고 부족)
            ProductService-->>Facade: CoreException(BAD_REQUEST)
            note over Facade, DB: 트랜잭션 롤백 → 주문 INSERT 및 모든 재고 차감 원상복구
            Facade-->>Controller: CoreException
            Controller-->>User: 400 Bad Request
        end
    end
    note over Facade, DB: @Transactional 종료
    Facade-->>Controller: OrderInfo
    Controller-->>User: 201 Created ApiResponse(OrderResponse)
```

---

## GET /api/v1/orders

```mermaid
sequenceDiagram
    actor User
    participant Controller as OrderV1Controller
    participant Facade as OrderFacade
    participant OrderService
    participant DB

    User->>Controller: GET /api/v1/orders?startAt=2026-01-01&endAt=2026-01-31
    alt startAt 또는 endAt 미입력
        Controller-->>User: 400 Bad Request
    end
    Controller->>Facade: getOrders(userId, startAt, endAt)
    Facade->>OrderService: getOrders(userId, startAt, endAt)
    OrderService->>DB: SELECT * FROM orders WHERE user_id = ? AND created_at BETWEEN ? AND ? AND deleted_at IS NULL
    OrderService-->>Facade: List~OrderModel~
    Facade-->>Controller: List~OrderInfo~
    Controller-->>User: 200 OK ApiResponse(List~OrderResponse~)
```

---

## GET /api/v1/orders/{orderId}

```mermaid
sequenceDiagram
    actor User
    participant Controller as OrderV1Controller
    participant Facade as OrderFacade
    participant OrderService
    participant DB

    User->>Controller: GET /api/v1/orders/{orderId}
    Controller->>Facade: getOrder(userId, orderId)
    Facade->>OrderService: getOrder(orderId)
    OrderService->>DB: SELECT o.*, oi.* FROM orders o JOIN order_items oi ON o.id = oi.order_id WHERE o.id = ? AND o.deleted_at IS NULL
    alt 존재하지 않는 주문
        OrderService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>User: 404 Not Found
    end
    alt 본인 주문 아님 (order.userId != loginUserId)
        Facade-->>Controller: CoreException(FORBIDDEN)
        Controller-->>User: 403 Forbidden
    end
    OrderService-->>Facade: OrderModel
    Facade-->>Controller: OrderInfo
    Controller-->>User: 200 OK ApiResponse(OrderDetailResponse)
```

---

## GET /api-admin/v1/orders

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as OrderAdminV1Controller
    participant Facade as OrderFacade
    participant OrderService
    participant DB

    Admin->>Controller: GET /api-admin/v1/orders?page=0&size=20
    Controller->>Facade: getOrders(page, size)
    Facade->>OrderService: getOrders(page, size)
    OrderService->>DB: SELECT * FROM orders WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT ? OFFSET ?
    OrderService-->>Facade: Page~OrderModel~
    Facade-->>Controller: Page~OrderInfo~
    Controller-->>Admin: 200 OK ApiResponse(Page~OrderResponse~)
```

---

## GET /api-admin/v1/orders/{orderId}

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as OrderAdminV1Controller
    participant Facade as OrderFacade
    participant OrderService
    participant DB

    Admin->>Controller: GET /api-admin/v1/orders/{orderId}
    Controller->>Facade: getOrder(orderId)
    Facade->>OrderService: getOrder(orderId)
    OrderService->>DB: SELECT o.*, oi.* FROM orders o JOIN order_items oi ON o.id = oi.order_id WHERE o.id = ? AND o.deleted_at IS NULL
    alt 존재하지 않는 주문
        OrderService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>Admin: 404 Not Found
    end
    OrderService-->>Facade: OrderModel
    Facade-->>Controller: OrderInfo
    Controller-->>Admin: 200 OK ApiResponse(OrderDetailResponse)
```
