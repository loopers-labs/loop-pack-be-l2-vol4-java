# 시퀀스 다이어그램

> 인증(유저 헤더 검증, 어드민 LDAP 검증) 로직은 핵심 비즈니스 흐름에 집중하기 위해 흐름에서 생략한다.
> 인증 필요 여부는 제목 배지와 다이어그램 상단 Note로 표시한다.
> 단순 조회/등록/수정(분기 없음) API는 다이어그램을 생략한다.

| 배지 | 의미 |
|---|---|
| `🔐 User` | X-Loopers-LoginId / X-Loopers-LoginPw 헤더 필요 |
| `🔐 Admin` | X-Loopers-Ldap: loopers.admin 헤더 필요 |
| (없음) | 인증 불필요 |

---

## Brand

### DELETE /api-admin/v1/brands/{brandId} — 브랜드 삭제 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over BrandAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant BrandAdminV1Controller
    participant BrandFacade
    participant BrandService
    participant ProductService
    participant ProductInventoryService
    participant LikeService

    BrandAdminV1Controller->>BrandFacade: deleteBrand(brandId)
    BrandFacade->>BrandService: delete(brandId)
    BrandService-->>BrandFacade: (없으면 404)

    BrandFacade->>ProductService: findIdsByBrand(brandId)
    ProductService-->>BrandFacade: List~productId~

    BrandFacade->>ProductService: deleteAll(productIds)
    BrandFacade->>ProductInventoryService: deleteAllByProducts(productIds)
    BrandFacade->>LikeService: deleteAllByProducts(productIds)

    BrandFacade-->>BrandAdminV1Controller: void
```

---

## Product

### POST /api-admin/v1/products — 상품 등록 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over ProductAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant ProductAdminV1Controller
    participant ProductFacade
    participant ProductService
    participant BrandRepository
    participant ProductRepository
    participant ProductInventoryRepository

    ProductAdminV1Controller->>ProductFacade: createProduct(brandId, name, description, price, quantity)
    ProductFacade->>ProductService: create(brandId, name, description, price, quantity)
    ProductService->>BrandRepository: findById(brandId)
    BrandRepository-->>ProductService: BrandModel (없으면 404)
    ProductService->>ProductRepository: save(new ProductModel)
    ProductRepository-->>ProductService: ProductModel
    ProductService->>ProductInventoryRepository: save(new ProductInventoryModel(productId, quantity))
    ProductInventoryRepository-->>ProductService: ProductInventoryModel
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
    participant ProductInventoryService
    participant ProductRepository
    participant ProductInventoryRepository

    ProductAdminV1Controller->>ProductFacade: updateProduct(productId, name, description, price, quantity)
    ProductFacade->>ProductService: update(productId, name, description, price)
    ProductService->>ProductRepository: findById(productId)
    ProductRepository-->>ProductService: ProductModel (없으면 404)
    Note over ProductService: brand 필드는 수정 대상에서 제외
    ProductService->>ProductService: product.update(name, description, price)
    ProductService-->>ProductFacade: ProductModel

    ProductFacade->>ProductInventoryService: updateQuantity(productId, quantity)
    ProductInventoryService->>ProductInventoryRepository: findByProductId(productId)
    ProductInventoryRepository-->>ProductInventoryService: ProductInventoryModel
    ProductInventoryService->>ProductInventoryService: inventory.updateQuantity(quantity)
    ProductInventoryService-->>ProductFacade: void

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
    participant ProductInventoryService
    participant LikeService

    ProductAdminV1Controller->>ProductFacade: deleteProduct(productId)
    ProductFacade->>ProductService: delete(productId)
    ProductService-->>ProductFacade: (없으면 404)
    ProductFacade->>ProductInventoryService: deleteByProduct(productId)
    ProductFacade->>LikeService: deleteAllByProduct(productId)
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
    ProductService-->>LikeFacade: ProductModel

    LikeFacade->>LikeService: addLike(userId, productId)
    LikeService->>LikeRepository: findByUserIdAndProductId(userId, productId)
    Note over LikeRepository: deleted_at 포함 전체 조회
    LikeRepository-->>LikeService: Optional~LikeModel~ (active 존재 시 409 Conflict)

    alt soft-deleted 존재
        LikeService->>LikeService: like.restore() [deleted_at=null]
    else 없음 (신규)
        LikeService->>LikeRepository: save(new LikeModel)
        LikeRepository-->>LikeService: LikeModel
    end
    LikeService-->>LikeFacade: void

    LikeFacade->>ProductService: incrementLikeCount(productId)
    ProductService->>ProductRepository: UPDATE like_count = like_count + 1
    ProductRepository-->>LikeFacade: void

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
    participant ProductService
    participant LikeRepository
    participant ProductRepository

    LikeV1Controller->>LikeFacade: removeLike(userId, productId)
    LikeFacade->>LikeService: removeLike(userId, productId)
    LikeService->>LikeRepository: findByUserIdAndProductId(userId, productId)
    LikeRepository-->>LikeService: LikeModel (없으면 404)
    LikeService->>LikeService: like.delete()

    LikeFacade->>ProductService: decrementLikeCount(productId)
    Note over ProductRepository: UPDATE product SET like_count = like_count - 1 WHERE id = ?
    ProductService->>ProductRepository: decrementLikeCount(productId)

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

    LikeV1Controller->>LikeFacade: getLikedProducts(authUserId, userId, page, size)
    Note over LikeFacade: path userId ≠ 인증된 userId 이면 403 Forbidden
    LikeFacade->>LikeService: findByUserId(userId, pageable)
    LikeService->>LikeRepository: findByUserId(userId, pageable)
    LikeRepository-->>LikeService: Page~LikeModel~
    LikeService-->>LikeFacade: productIds (+ totalElements)

    LikeFacade->>ProductService: findAllByIds(productIds)
    ProductService->>ProductRepository: findAllById(productIds)
    ProductRepository-->>ProductService: List~ProductModel~
    ProductService-->>LikeFacade: List~ProductModel~

    LikeFacade-->>LikeV1Controller: List~ProductInfo~
```

---

## Order

### POST /api/v1/orders — 주문 생성 `🔐 User`

> 흐름: 상품 조회(재고 포함) → 재고 확인(fast fail) → 주문 생성 → 재고 차감
> fast-fail은 명백한 재고 부족을 조기 차단하는 역할이며, 실제 원자성은 재고 차감 단계의 FOR UPDATE 락이 보장한다.
> 재고 차감 실패 시 @Transactional로 주문 생성까지 전체 롤백된다.

```mermaid
sequenceDiagram
    Note over OrderV1Controller: 🔐 X-Loopers-LoginId / X-Loopers-LoginPw
    participant OrderV1Controller
    participant OrderFacade
    participant ProductService
    participant OrderService
    participant ProductRepository
    participant ProductInventoryRepository
    participant OrderRepository

    OrderV1Controller->>OrderFacade: createOrder(userId, items)

    Note over OrderFacade,ProductRepository: [ 트랜잭션 외부 — 읽기 전용 ]

    OrderFacade->>ProductService: getProducts(productIds)
    ProductService->>ProductRepository: findAllByIds(productIds)
    ProductRepository-->>ProductService: List~ProductModel~ (없는 상품 있으면 404)
    ProductService-->>OrderFacade: List~ProductModel~

    Note over OrderFacade: 재고 확인 (fast fail, 락 없음) — product.quantity < 요청수량이면 400 Bad Request

    Note over OrderFacade,OrderRepository: ── @Transactional 시작 ──

    OrderFacade->>OrderService: createOrder(userId, items + snapshot)
    OrderService->>OrderRepository: save(OrderModel + OrderItemModel)
    OrderRepository-->>OrderService: OrderModel
    OrderService-->>OrderFacade: OrderModel

    OrderFacade->>ProductService: deductInventories(productId-quantity 쌍)
    Note over ProductService: productId 오름차순 정렬 (데드락 방어)
    ProductService->>ProductInventoryRepository: findAllByProductIds(productIds) FOR UPDATE
    Note over ProductInventoryRepository: WHERE product_id IN (...) FOR UPDATE
    ProductService->>ProductService: 각 inventory.deduct(quantity)

    Note over OrderFacade,OrderRepository: ── 성공 시 Commit / 재고 부족 시 전체 Rollback ──

    OrderFacade-->>OrderV1Controller: OrderInfo
```
