# 02. 시퀀스 다이어그램 — 상품 목록 / 상품 상세 / 브랜드 조회 / 상품 좋아요

## 1. 상품 목록 조회 (필터 + 정렬 + 페이징)

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ProductV1Controller
    participant Facade as ProductFacade
    participant Service as ProductService
    participant DB as MySQL

    Client->>Controller: GET /api/v1/products?brandId=1&sort=latest&page=0&size=20
    Controller->>Facade: getProducts(brandId, sort, page, size)
    Facade->>Service: getProducts(brandId, sort, pageable)
    Service->>DB: 조건부 조회 (brandId 필터, sort 정렬, 페이징, deleted_at IS NULL)
    DB-->>Service: Page<ProductModel>
    Service-->>Facade: Page<ProductModel>
    Facade-->>Controller: PagedProductInfo
    Controller-->>Client: ApiResponse<PagedProductResponse>
```

## 2. 상품 상세 조회

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ProductV1Controller
    participant Facade as ProductFacade
    participant Service as ProductService
    participant DB as MySQL

    Client->>Controller: GET /api/v1/products/{productId}
    Controller->>Facade: getProduct(productId)
    Facade->>Service: getProduct(productId)
    Service->>DB: Product + Brand fetch join (deleted_at IS NULL)

    alt 상품 없음
        Service-->>Client: 404 NOT_FOUND
    else 상품 존재
        DB-->>Service: ProductModel (with Brand)
        Service-->>Facade: ProductModel
        Facade-->>Controller: ProductInfo (brandId, brandName 포함)
        Controller-->>Client: ApiResponse<ProductResponse>
    end
```

## 3. 브랜드 정보 조회

```mermaid
sequenceDiagram
    actor Client
    participant Controller as BrandV1Controller
    participant Facade as BrandFacade
    participant Service as BrandService
    participant DB as MySQL

    Client->>Controller: GET /api/v1/brands/{brandId}
    Controller->>Facade: getBrand(brandId)
    Facade->>Service: getBrand(brandId)
    Service->>DB: Brand 조회 (deleted_at IS NULL)

    alt 브랜드 없음
        Service-->>Client: 404 NOT_FOUND
    else 브랜드 존재
        DB-->>Service: BrandModel
        Service-->>Facade: BrandModel
        Facade-->>Controller: BrandInfo
        Controller-->>Client: ApiResponse<BrandResponse>
    end
```

---

## 4. 상품 좋아요 등록 (멱등)

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ProductLikeV1Controller
    participant Facade as ProductLikeFacade
    participant LikeService as ProductLikeService
    participant ProductService as ProductService
    participant DB as MySQL

    Client->>Controller: POST /api/v1/products/{productId}/likes (X-User-Id: 1)
    Controller->>Facade: like(userId, productId)
    Facade->>ProductService: getProduct(productId)

    alt 상품 없음
        ProductService-->>Client: 404 NOT_FOUND
    else 상품 존재
        Facade->>LikeService: like(userId, productId)
        LikeService->>DB: 기존 좋아요 조회 (user_id + product_id)

        alt 이미 좋아요 존재
            LikeService-->>Facade: 무시 (멱등)
        else 좋아요 없음
            LikeService->>DB: INSERT product_like
            LikeService->>ProductService: incrementLikeCount(productId)
            ProductService->>DB: like_count + 1
        end

        Facade-->>Controller: void
        Controller-->>Client: ApiResponse (SUCCESS)
    end
```

## 5. 상품 좋아요 취소 (멱등)

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ProductLikeV1Controller
    participant Facade as ProductLikeFacade
    participant LikeService as ProductLikeService
    participant ProductService as ProductService
    participant DB as MySQL

    Client->>Controller: DELETE /api/v1/products/{productId}/likes (X-User-Id: 1)
    Controller->>Facade: unlike(userId, productId)
    Facade->>ProductService: getProduct(productId)

    alt 상품 없음
        ProductService-->>Client: 404 NOT_FOUND
    else 상품 존재
        Facade->>LikeService: unlike(userId, productId)
        LikeService->>DB: 기존 좋아요 조회 (user_id + product_id)

        alt 좋아요 없음
            LikeService-->>Facade: 무시 (멱등)
        else 좋아요 존재
            LikeService->>DB: DELETE product_like (물리 삭제)
            LikeService->>ProductService: decrementLikeCount(productId)
            ProductService->>DB: like_count - 1
        end

        Facade-->>Controller: void
        Controller-->>Client: ApiResponse (SUCCESS)
    end
```

---

## 7. 주문 생성

```mermaid
sequenceDiagram
    actor Client
    participant Controller as OrderV1Controller
    participant Facade as OrderFacade
    participant ProductSvc as ProductService
    participant OrderSvc as OrderService
    participant ProductRepo as ProductRepository
    participant OrderRepo as OrderRepository

    Client->>Controller: POST /api/v1/orders (X-Loopers-LoginId/Pw)
    Controller->>Facade: createOrder(memberId, items)

    loop 각 상품별 재고 확인
        Facade->>ProductSvc: getProduct(productId)
        ProductSvc->>ProductRepo: findById(productId)
        ProductRepo-->>ProductSvc: ProductModel
        ProductSvc-->>Facade: ProductModel
        Note over Facade: stock < quantity → CoreException(BAD_REQUEST)
    end

    loop 각 상품별 재고 차감
        Facade->>ProductSvc: deductStock(productId, quantity)
        ProductSvc->>ProductRepo: save(product)
    end

    Facade->>OrderSvc: createOrder(memberId, snapshotItems, totalPrice)
    OrderSvc->>OrderRepo: save(OrderModel + OrderItemModels)
    OrderRepo-->>OrderSvc: OrderModel
    OrderSvc-->>Facade: OrderModel
    Facade-->>Controller: OrderInfo
    Controller-->>Client: ApiResponse~OrderResponse~
```

## 8. 결제 요청 (stub)

```mermaid
sequenceDiagram
    actor Client
    participant Controller as PaymentV1Controller
    participant Facade as PaymentFacade
    participant OrderSvc as OrderService
    participant PaymentSvc as PaymentService
    participant OrderRepo as OrderRepository
    participant PaymentRepo as PaymentRepository

    Client->>Controller: POST /api/v1/payments { orderId }
    Controller->>Facade: processPayment(memberId, orderId)
    Facade->>OrderSvc: getOrder(orderId)
    OrderSvc->>OrderRepo: findById(orderId)
    OrderRepo-->>OrderSvc: OrderModel

    alt status != PENDING
        OrderSvc-->>Client: 409 CONFLICT
    else PENDING
        Facade->>OrderSvc: updateStatus(orderId, PAID)
        OrderSvc->>OrderRepo: save(order)
        Facade->>PaymentSvc: createPayment(orderId, amount)
        PaymentSvc->>PaymentRepo: save(PaymentModel)
        PaymentRepo-->>PaymentSvc: PaymentModel
        PaymentSvc-->>Facade: PaymentModel
        Facade-->>Controller: PaymentInfo
        Controller-->>Client: ApiResponse~PaymentResponse~
    end
```

## 9. 주문 목록 조회 (유저)

```mermaid
sequenceDiagram
    actor Client
    participant Controller as OrderV1Controller
    participant Facade as OrderFacade
    participant OrderSvc as OrderService
    participant DB as MySQL

    Client->>Controller: GET /api/v1/orders?startAt=...&endAt=... (X-Loopers-LoginId/Pw)
    Controller->>Facade: getOrders(memberId, startAt, endAt)
    Facade->>OrderSvc: getOrders(memberId, startAt, endAt)
    OrderSvc->>DB: memberId + createdAt BETWEEN startAt AND endAt, ORDER BY createdAt DESC
    DB-->>OrderSvc: List~OrderModel~
    OrderSvc-->>Facade: List~OrderModel~
    Facade-->>Controller: List~OrderInfo~
    Controller-->>Client: ApiResponse~List~OrderResponse~~
```

## 10. [Admin] 주문 목록 조회

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as OrderAdminV1Controller
    participant Facade as OrderFacade
    participant OrderSvc as OrderService
    participant DB as MySQL

    Admin->>Controller: GET /api-admin/v1/orders?page=0&size=20 (X-Loopers-Ldap: loopers.admin)
    Note over Controller: Ldap 헤더 검증 (loopers.admin 아니면 401)
    Controller->>Facade: getAllOrders(page, size)
    Facade->>OrderSvc: getAllOrders(pageable)
    OrderSvc->>DB: 전체 주문 조회, ORDER BY createdAt DESC
    DB-->>OrderSvc: Page~OrderModel~
    OrderSvc-->>Facade: Page~OrderModel~
    Facade-->>Controller: PagedOrderInfo
    Controller-->>Admin: ApiResponse~PagedOrderResponse~
```

## 6. 내가 좋아요한 상품 목록 조회

```mermaid
sequenceDiagram
    actor Client
    participant Controller as ProductLikeV1Controller
    participant Facade as ProductLikeFacade
    participant LikeService as ProductLikeService
    participant DB as MySQL

    Client->>Controller: GET /api/v1/users/{userId}/likes?page=0&size=20 (X-User-Id: 1)
    Controller->>Facade: getMyLikes(userId, page, size)
    Facade->>LikeService: getMyLikes(userId, pageable)
    LikeService->>DB: product_like JOIN product (ORDER BY pl.created_at DESC, 페이징)
    DB-->>LikeService: Page<ProductModel>
    LikeService-->>Facade: Page<ProductModel>
    Facade-->>Controller: PagedProductInfo
    Controller-->>Client: ApiResponse<PagedProductResponse>
```
