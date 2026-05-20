# 시퀀스 다이어그램

> 인증(유저 헤더 검증, 어드민 LDAP 검증) 로직은 핵심 비즈니스 흐름에 집중하기 위해 흐름에서 생략한다.
> 인증 필요 여부는 제목 배지와 다이어그램 상단 Note로 표시한다.

| 배지 | 의미 |
|---|---|
| `🔐 User` | X-Loopers-LoginId / X-Loopers-LoginPw 헤더 필요 |
| `🔐 Admin` | X-Loopers-Ldap: loopers.admin 헤더 필요 |
| (없음) | 인증 불필요 |

---

## Brand

### GET /api/v1/brands/{brandId} — 브랜드 단건 조회

```mermaid
sequenceDiagram
    participant BrandV1Controller
    participant BrandFacade
    participant BrandService
    participant BrandRepository

    BrandV1Controller->>BrandFacade: getBrand(brandId)
    BrandFacade->>BrandService: getBrand(brandId)
    BrandService->>BrandRepository: findById(brandId)
    BrandRepository-->>BrandService: BrandModel (없으면 404)
    BrandService-->>BrandFacade: BrandModel
    BrandFacade-->>BrandV1Controller: BrandInfo
```

---

### GET /api-admin/v1/brands — 브랜드 목록 조회 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over BrandAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant BrandAdminV1Controller
    participant BrandFacade
    participant BrandService
    participant BrandRepository

    BrandAdminV1Controller->>BrandFacade: getBrands(page, size)
    BrandFacade->>BrandService: getBrands(page, size)
    BrandService->>BrandRepository: findAll(pageable)
    BrandRepository-->>BrandService: Page~BrandModel~
    BrandService-->>BrandFacade: Page~BrandModel~
    BrandFacade-->>BrandAdminV1Controller: Page~BrandInfo~
```

---

### POST /api-admin/v1/brands — 브랜드 등록 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over BrandAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant BrandAdminV1Controller
    participant BrandFacade
    participant BrandService
    participant BrandRepository

    BrandAdminV1Controller->>BrandFacade: createBrand(name, description)
    BrandFacade->>BrandService: create(name, description)
    BrandService->>BrandRepository: save(new BrandModel)
    BrandRepository-->>BrandService: BrandModel
    BrandService-->>BrandFacade: BrandModel
    BrandFacade-->>BrandAdminV1Controller: BrandInfo
```

---

### PUT /api-admin/v1/brands/{brandId} — 브랜드 수정 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over BrandAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant BrandAdminV1Controller
    participant BrandFacade
    participant BrandService
    participant BrandRepository

    BrandAdminV1Controller->>BrandFacade: updateBrand(brandId, name, description)
    BrandFacade->>BrandService: update(brandId, name, description)
    BrandService->>BrandRepository: findById(brandId)
    BrandRepository-->>BrandService: BrandModel (없으면 404)
    BrandService->>BrandService: brand.update(name, description)
    BrandService-->>BrandFacade: BrandModel
    BrandFacade-->>BrandAdminV1Controller: BrandInfo
```

---

### DELETE /api-admin/v1/brands/{brandId} — 브랜드 삭제 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over BrandAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant BrandAdminV1Controller
    participant BrandFacade
    participant BrandService
    participant ProductService
    participant BrandRepository
    participant ProductRepository

    BrandAdminV1Controller->>BrandFacade: deleteBrand(brandId)
    BrandFacade->>BrandService: delete(brandId)
    BrandService->>BrandRepository: findById(brandId)
    BrandRepository-->>BrandService: BrandModel (없으면 404)
    BrandService->>BrandService: brand.delete()

    BrandFacade->>ProductService: deleteAllByBrand(brandId)
    ProductService->>ProductRepository: findAllByBrandId(brandId)
    ProductRepository-->>ProductService: List~ProductModel~
    loop 상품별
        ProductService->>ProductService: product.delete()
    end
```

---

## Product

### GET /api/v1/products — 상품 목록 조회

```mermaid
sequenceDiagram
    participant ProductV1Controller
    participant ProductFacade
    participant ProductService
    participant LikeService
    participant ProductRepository
    participant LikeRepository

    ProductV1Controller->>ProductFacade: getProducts(brandId, sort, page, size)
    ProductFacade->>ProductService: getProducts(brandId, sort, page, size)
    ProductService->>ProductRepository: findAll(brandId, sort, pageable)
    Note over ProductRepository: Brand JOIN으로 brandName 함께 조회
    ProductRepository-->>ProductService: Page~ProductModel~
    ProductService-->>ProductFacade: Page~ProductModel~

    ProductFacade->>LikeService: countByProductIds(productIds)
    LikeService->>LikeRepository: countByProductIdIn(productIds)
    Note over LikeRepository: GROUP BY product_id → Map(productId, count)
    LikeRepository-->>LikeService: Map~Long, Long~
    LikeService-->>ProductFacade: Map~Long, Long~

    ProductFacade-->>ProductV1Controller: Page~ProductInfo~
```

---

### GET /api/v1/products/{productId} — 상품 단건 조회

```mermaid
sequenceDiagram
    participant ProductV1Controller
    participant ProductFacade
    participant ProductService
    participant LikeService
    participant ProductRepository
    participant LikeRepository

    ProductV1Controller->>ProductFacade: getProduct(productId)
    ProductFacade->>ProductService: getProduct(productId)
    ProductService->>ProductRepository: findById(productId)
    Note over ProductRepository: Brand JOIN으로 brandName 함께 조회
    ProductRepository-->>ProductService: ProductModel (없으면 404)
    ProductService-->>ProductFacade: ProductModel

    ProductFacade->>LikeService: countByProductId(productId)
    LikeService->>LikeRepository: countByProductId(productId)
    LikeRepository-->>LikeService: likeCount
    LikeService-->>ProductFacade: likeCount

    ProductFacade-->>ProductV1Controller: ProductInfo
```

---

### POST /api-admin/v1/products — 상품 등록 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over ProductAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant ProductAdminV1Controller
    participant ProductFacade
    participant ProductService
    participant BrandRepository
    participant ProductRepository
    participant ProductStockRepository

    ProductAdminV1Controller->>ProductFacade: createProduct(brandId, name, description, price, quantity)
    ProductFacade->>ProductService: create(brandId, name, description, price, quantity)
    ProductService->>BrandRepository: findById(brandId)
    BrandRepository-->>ProductService: BrandModel (없으면 404)
    ProductService->>ProductRepository: save(new ProductModel)
    ProductRepository-->>ProductService: ProductModel
    ProductService->>ProductStockRepository: save(new ProductStockModel(productId, quantity))
    ProductStockRepository-->>ProductService: ProductStockModel
    ProductService-->>ProductFacade: ProductModel
    ProductFacade-->>ProductAdminV1Controller: ProductInfo
```

---

### PUT /api-admin/v1/products/{productId} — 상품 수정 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over ProductAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant ProductAdminV1Controller
    participant ProductFacade
    participant ProductService
    participant ProductRepository

    ProductAdminV1Controller->>ProductFacade: updateProduct(productId, name, description, price)
    ProductFacade->>ProductService: update(productId, name, description, price)
    ProductService->>ProductRepository: findById(productId)
    ProductRepository-->>ProductService: ProductModel (없으면 404)
    Note over ProductService: brand 필드는 수정 대상에서 제외
    ProductService->>ProductService: product.update(name, description, price)
    ProductService-->>ProductFacade: ProductModel
    ProductFacade-->>ProductAdminV1Controller: ProductInfo
```

---

### DELETE /api-admin/v1/products/{productId} — 상품 삭제 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over ProductAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant ProductAdminV1Controller
    participant ProductFacade
    participant ProductService
    participant ProductRepository

    ProductAdminV1Controller->>ProductFacade: deleteProduct(productId)
    ProductFacade->>ProductService: delete(productId)
    ProductService->>ProductRepository: findById(productId)
    ProductRepository-->>ProductService: ProductModel (없으면 404)
    ProductService->>ProductService: product.delete()
    ProductFacade-->>ProductAdminV1Controller: void
```

---

## Like

### POST /api/v1/products/{productId}/likes — 좋아요 등록 `🔐 User`

```mermaid
sequenceDiagram
    Note over LikeV1Controller: 🔐 X-Loopers-LoginId / X-Loopers-LoginPw
    participant LikeV1Controller
    participant LikeFacade
    participant ProductService
    participant LikeService
    participant ProductRepository
    participant LikeRepository

    LikeV1Controller->>LikeFacade: addLike(userId, productId)
    LikeFacade->>ProductService: getProduct(productId)
    ProductService->>ProductRepository: findById(productId)
    ProductRepository-->>ProductService: ProductModel (없으면 404)

    LikeFacade->>LikeService: addLike(userId, productId)
    LikeService->>LikeRepository: findByUserIdAndProductId(userId, productId)
    LikeRepository-->>LikeService: Optional~LikeModel~
    Note over LikeService: 존재하면 409 Conflict
    LikeService->>LikeRepository: save(new LikeModel)
    LikeFacade-->>LikeV1Controller: void
```

---

### DELETE /api/v1/products/{productId}/likes — 좋아요 취소 `🔐 User`

```mermaid
sequenceDiagram
    Note over LikeV1Controller: 🔐 X-Loopers-LoginId / X-Loopers-LoginPw
    participant LikeV1Controller
    participant LikeFacade
    participant LikeService
    participant LikeRepository

    LikeV1Controller->>LikeFacade: removeLike(userId, productId)
    LikeFacade->>LikeService: removeLike(userId, productId)
    LikeService->>LikeRepository: findByUserIdAndProductId(userId, productId)
    LikeRepository-->>LikeService: LikeModel (없으면 404)
    LikeService->>LikeService: like.delete()
    LikeFacade-->>LikeV1Controller: void
```

---

### GET /api/v1/users/{userId}/likes — 내가 좋아요한 상품 목록 `🔐 User`

```mermaid
sequenceDiagram
    Note over LikeV1Controller: 🔐 X-Loopers-LoginId / X-Loopers-LoginPw
    participant LikeV1Controller
    participant LikeFacade
    participant LikeService
    participant ProductService
    participant LikeRepository
    participant ProductRepository

    LikeV1Controller->>LikeFacade: getLikedProducts(authUserId, userId)
    Note over LikeFacade: path userId ≠ 인증된 userId 이면 403 Forbidden
    LikeFacade->>LikeService: findByUserId(userId)
    LikeService->>LikeRepository: findByUserId(userId)
    LikeRepository-->>LikeService: List~LikeModel~
    LikeService-->>LikeFacade: productIds

    LikeFacade->>ProductService: findAllByIds(productIds)
    ProductService->>ProductRepository: findAllById(productIds)
    Note over ProductRepository: Brand JOIN으로 brandName 함께 조회
    ProductRepository-->>ProductService: List~ProductModel~
    ProductService-->>LikeFacade: List~ProductModel~

    LikeFacade->>LikeService: countByProductIds(productIds)
    LikeService->>LikeRepository: countByProductIdIn(productIds)
    Note over LikeRepository: GROUP BY product_id → Map(productId, count)
    LikeRepository-->>LikeService: Map~Long, Long~
    LikeService-->>LikeFacade: Map~Long, Long~

    LikeFacade-->>LikeV1Controller: List~ProductInfo~
```

---

## Order

### POST /api/v1/orders — 주문 생성 `🔐 User`

> 흐름: 제품 조회 → 재고 확인(fast fail) → 주문 생성 → 재고 차감
> 재고 확인은 명백한 재고 부족을 조기 차단하는 역할이며, 실제 원자성은 재고 차감 단계의 FOR UPDATE 락이 보장한다.
> 재고 차감 실패 시 @Transactional로 주문 생성까지 전체 롤백된다.

```mermaid
sequenceDiagram
    Note over OrderV1Controller: 🔐 X-Loopers-LoginId / X-Loopers-LoginPw
    participant OrderV1Controller
    participant OrderFacade
    participant ProductService
    participant OrderService
    participant ProductRepository
    participant ProductStockRepository
    participant OrderRepository

    OrderV1Controller->>OrderFacade: createOrder(userId, items)

    Note over OrderFacade,ProductRepository: [ 트랜잭션 외부 — 읽기 전용 ]

    loop 상품별
        OrderFacade->>ProductService: getProduct(productId)
        ProductService->>ProductRepository: findById(productId)
        ProductRepository-->>ProductService: ProductModel (없으면 404)
        ProductService-->>OrderFacade: ProductModel
    end

    loop 상품별
        OrderFacade->>ProductService: getStock(productId)
        ProductService->>ProductStockRepository: findByProductId(productId)
        ProductStockRepository-->>ProductService: ProductStockModel
        ProductService-->>OrderFacade: ProductStockModel
    end

    Note over OrderFacade: 재고 확인 (fast fail, 락 없음) — 하나라도 부족하면 400 Bad Request

    Note over OrderFacade,OrderRepository: ── @Transactional 시작 ──

    OrderFacade->>OrderService: createOrder(userId, items + snapshot)
    OrderService->>OrderRepository: save(OrderModel + OrderItemModel)
    OrderRepository-->>OrderService: OrderModel
    OrderService-->>OrderFacade: OrderModel

    loop 상품별
        OrderFacade->>ProductService: deductStock(productId, quantity)
        ProductService->>ProductStockRepository: findByProductId (FOR UPDATE)
        ProductService->>ProductService: productStock.deduct(quantity)
    end

    Note over OrderFacade,OrderRepository: ── 성공 시 Commit / 재고 부족 시 전체 Rollback ──

    OrderFacade-->>OrderV1Controller: OrderInfo
```

---

### GET /api/v1/orders — 내 주문 목록 조회 `🔐 User`

```mermaid
sequenceDiagram
    Note over OrderV1Controller: 🔐 X-Loopers-LoginId / X-Loopers-LoginPw
    participant OrderV1Controller
    participant OrderFacade
    participant OrderService
    participant OrderRepository

    OrderV1Controller->>OrderFacade: getOrders(userId, startAt, endAt)
    OrderFacade->>OrderService: getOrders(userId, startAt, endAt)
    OrderService->>OrderRepository: findByUserIdAndCreatedAtBetween(userId, startAt, endAt)
    OrderRepository-->>OrderService: List~OrderModel~
    OrderService-->>OrderFacade: List~OrderModel~
    OrderFacade-->>OrderV1Controller: List~OrderInfo~
```

---

### GET /api/v1/orders/{orderId} — 주문 단건 조회 `🔐 User`

```mermaid
sequenceDiagram
    Note over OrderV1Controller: 🔐 X-Loopers-LoginId / X-Loopers-LoginPw
    participant OrderV1Controller
    participant OrderFacade
    participant OrderService
    participant OrderRepository

    OrderV1Controller->>OrderFacade: getOrder(userId, orderId)
    OrderFacade->>OrderService: getOrder(userId, orderId)
    OrderService->>OrderRepository: findById(orderId)
    OrderRepository-->>OrderService: OrderModel (없으면 404)
    Note over OrderService: 타 유저 주문 접근 불가 — userId 불일치 시 404
    OrderService-->>OrderFacade: OrderModel
    OrderFacade-->>OrderV1Controller: OrderInfo
```

---

### GET /api-admin/v1/orders — 주문 목록 조회 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over OrderAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant OrderAdminV1Controller
    participant OrderFacade
    participant OrderService
    participant OrderRepository

    OrderAdminV1Controller->>OrderFacade: getOrders(page, size)
    OrderFacade->>OrderService: getOrders(page, size)
    OrderService->>OrderRepository: findAll(pageable)
    OrderRepository-->>OrderService: Page~OrderModel~
    OrderService-->>OrderFacade: Page~OrderModel~
    OrderFacade-->>OrderAdminV1Controller: Page~OrderInfo~
```

---

### GET /api-admin/v1/orders/{orderId} — 주문 단건 조회 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over OrderAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant OrderAdminV1Controller
    participant OrderFacade
    participant OrderService
    participant OrderRepository

    OrderAdminV1Controller->>OrderFacade: getOrder(orderId)
    OrderFacade->>OrderService: getOrder(orderId)
    OrderService->>OrderRepository: findById(orderId)
    OrderRepository-->>OrderService: OrderModel (없으면 404)
    OrderService-->>OrderFacade: OrderModel
    OrderFacade-->>OrderAdminV1Controller: OrderInfo
```
