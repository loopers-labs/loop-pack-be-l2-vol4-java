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
    participant InventoryService
    participant LikeService

    BrandAdminV1Controller->>BrandFacade: deleteBrand(brandId)
    BrandFacade->>BrandService: delete(brandId)
    BrandService-->>BrandFacade: (없으면 404)

    BrandFacade->>ProductService: findIdsByBrand(brandId)
    ProductService-->>BrandFacade: List~productId~

    BrandFacade->>ProductService: deleteAll(productIds)
    BrandFacade->>InventoryService: deleteAllByProducts(productIds)
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
    participant InventoryRepository

    ProductAdminV1Controller->>ProductFacade: createProduct(brandId, name, description, price, quantity)
    ProductFacade->>ProductService: create(brandId, name, description, price, quantity)
    ProductService->>BrandRepository: findById(brandId)
    BrandRepository-->>ProductService: BrandEntity (없으면 404)
    ProductService->>ProductRepository: save(new ProductEntity)
    ProductRepository-->>ProductService: ProductEntity
    ProductService->>InventoryRepository: save(new InventoryEntity(productId, quantity))
    InventoryRepository-->>ProductService: InventoryEntity
    ProductService-->>ProductFacade: ProductEntity
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
    participant InventoryService
    participant ProductRepository
    participant InventoryRepository

    ProductAdminV1Controller->>ProductFacade: updateProduct(productId, name, description, price, quantity)
    ProductFacade->>ProductService: update(productId, name, description, price)
    ProductService->>ProductRepository: findById(productId)
    ProductRepository-->>ProductService: ProductEntity (없으면 404)
    Note over ProductService: brand 필드는 수정 대상에서 제외
    ProductService->>ProductService: product.update(name, description, price)
    ProductService-->>ProductFacade: ProductEntity

    ProductFacade->>InventoryService: updateQuantity(productId, quantity)
    InventoryService->>InventoryRepository: findByProductId(productId)
    InventoryRepository-->>InventoryService: InventoryEntity
    InventoryService->>InventoryService: inventory.updateQuantity(quantity)
    InventoryService-->>ProductFacade: void

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
    participant InventoryService
    participant LikeService

    ProductAdminV1Controller->>ProductFacade: deleteProduct(productId)
    ProductFacade->>ProductService: delete(productId)
    ProductService-->>ProductFacade: (없으면 404)
    ProductFacade->>InventoryService: deleteByProduct(productId)
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
    ProductRepository-->>ProductService: ProductEntity (없으면 404)
    ProductService-->>LikeFacade: ProductEntity

    LikeFacade->>LikeService: addLike(userId, productId)
    LikeService->>LikeRepository: findByUserIdAndProductId(userId, productId)
    Note over LikeRepository: deleted_at 포함 전체 조회
    LikeRepository-->>LikeService: Optional~LikeEntity~ (active 존재 시 409 Conflict)

    alt soft-deleted 존재
        LikeService->>LikeService: like.restore() [deleted_at=null]
    else 없음 (신규)
        LikeService->>LikeRepository: save(new LikeEntity)
        LikeRepository-->>LikeService: LikeEntity
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
    LikeRepository-->>LikeService: LikeEntity (없으면 404)
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
    LikeRepository-->>LikeService: Page~LikeEntity~
    LikeService-->>LikeFacade: productIds (+ totalElements)

    LikeFacade->>ProductService: findAllByIds(productIds)
    ProductService->>ProductRepository: findAllById(productIds)
    ProductRepository-->>ProductService: List~ProductEntity~
    ProductService-->>LikeFacade: List~ProductEntity~

    LikeFacade-->>LikeV1Controller: List~ProductInfo~
```

---

## Order

### POST /api/v1/orders — 주문 생성 `🔐 User`

> 흐름: 상품 조회 → originalAmount 계산 → [쿠폰 사용 → 재고 차감 → 주문 생성]
> 트랜잭션 내부 순서 변경: 실패 확률이 높은 쿠폰 사용을 먼저 수행해 불필요한 INSERT를 방지 (ADR-032)
> 쿠폰 없는 주문은 쿠폰 관련 단계를 건너뜀.

```mermaid
sequenceDiagram
    Note over OrderV1Controller: 🔐 X-Loopers-LoginId / X-Loopers-LoginPw
    participant OrderV1Controller
    participant OrderFacade
    participant ProductService
    participant CouponApplicationService
    participant InventoryService
    participant OrderService
    participant ProductRepository
    participant CouponRepository
    participant InventoryRepository
    participant OrderRepository

    OrderV1Controller->>OrderFacade: createOrder(userId, items, couponId?)

    Note over OrderFacade,ProductRepository: [ 트랜잭션 외부 — 읽기 전용 ]

    OrderFacade->>ProductService: getProducts(productIds)
    ProductService->>ProductRepository: findAllByIds(productIds)
    ProductRepository-->>ProductService: List~ProductEntity~ (없는 상품 있으면 404)
    ProductService-->>OrderFacade: List~ProductEntity~

    Note over OrderFacade: originalAmount = Σ(product.price × quantity)

    Note over OrderFacade,OrderRepository: ── @Transactional 시작 ──

    opt couponId 있음
        Note over OrderFacade: ① 쿠폰 유효성 검증 + 사용 처리 (PESSIMISTIC_WRITE, ADR-031)
        Note over OrderFacade: 검증은 락 획득 후 단일 지점에서만 수행 (이중 검증 없음)
        OrderFacade->>CouponApplicationService: useCoupon(couponId, userId, originalAmount)
        CouponApplicationService->>CouponRepository: findByIdWithLock(couponId)
        Note over CouponRepository: SELECT ... FOR UPDATE (PESSIMISTIC_WRITE)
        CouponRepository-->>CouponApplicationService: CouponEntity (없으면 404)
        Note over CouponApplicationService: isOwnedBy(userId) → 불일치 시 403
        CouponApplicationService->>CouponTemplateRepository: findById(coupon.couponTemplateId)
        CouponTemplateRepository-->>CouponApplicationService: CouponTemplateEntity
        Note over CouponApplicationService: resolveStatus(template.expiredAt) → EXPIRED 시 400
        Note over CouponApplicationService: status == USED 시 400
        Note over CouponApplicationService: template.validateMinOrderAmount(originalAmount) → 400
        CouponApplicationService->>CouponApplicationService: discountAmount = template.calculateDiscount(originalAmount)
        CouponApplicationService->>CouponApplicationService: coupon.use() — AVAILABLE → USED (인메모리)
        CouponApplicationService->>CouponRepository: save(coupon)
        CouponApplicationService-->>OrderFacade: discountAmount
    end

    Note over OrderFacade: ② 재고 차감 (productId 오름차순 정렬, ADR-014)
    OrderFacade->>InventoryService: deductAll(productId-quantity 쌍)
    InventoryService->>InventoryRepository: findAllByProductIds(productIds) FOR UPDATE
    Note over InventoryRepository: WHERE product_id IN (...) ORDER BY product_id FOR UPDATE
    InventoryService->>InventoryService: 각 inventory.deduct(quantity) — 재고 부족 시 400

    Note over OrderFacade: ③ 주문 엔티티 생성 및 저장
    OrderFacade->>OrderService: createOrder(userId, items, originalAmount, discountAmount, finalAmount, couponId?)
    OrderService->>OrderRepository: save(OrderEntity + OrderSnapshot)
    OrderRepository-->>OrderService: OrderEntity
    OrderService-->>OrderFacade: OrderEntity

    Note over OrderFacade,OrderRepository: ── 성공 시 Commit / 실패 시 전체 Rollback ──

    OrderFacade-->>OrderV1Controller: OrderInfo
```

---

## Coupon

### POST /api/v1/coupons/{couponTemplateId}/issue — 쿠폰 발급 `🔐 User`

```mermaid
sequenceDiagram
    Note over CouponV1Controller: 🔐 X-Loopers-LoginId / X-Loopers-LoginPw
    participant CouponV1Controller
    participant CouponApplicationService
    participant CouponTemplateRepository
    participant CouponRepository

    CouponV1Controller->>CouponApplicationService: issueCoupon(userId, couponTemplateId)
    CouponApplicationService->>CouponTemplateRepository: findById(couponTemplateId)
    CouponTemplateRepository-->>CouponApplicationService: CouponTemplateEntity (없으면 404)
    Note over CouponApplicationService: template.isExpired() → 만료됐으면 400
    CouponApplicationService->>CouponRepository: save(new CouponEntity(templateId, userId, AVAILABLE))
    CouponRepository-->>CouponApplicationService: CouponEntity
    CouponApplicationService-->>CouponV1Controller: CouponInfo (couponId)
```

---

### DELETE /api-admin/v1/coupons/{couponTemplateId} — 쿠폰 템플릿 삭제 `🔐 Admin`

```mermaid
sequenceDiagram
    Note over CouponAdminV1Controller: 🔐 X-Loopers-Ldap: loopers.admin
    participant CouponAdminV1Controller
    participant CouponApplicationService
    participant CouponTemplateRepository
    participant CouponRepository

    CouponAdminV1Controller->>CouponApplicationService: deleteTemplate(couponTemplateId)
    CouponApplicationService->>CouponTemplateRepository: findById(couponTemplateId)
    CouponTemplateRepository-->>CouponApplicationService: CouponTemplateEntity (없으면 404)
    CouponApplicationService->>CouponApplicationService: couponTemplate.delete()
    CouponApplicationService->>CouponTemplateRepository: save(couponTemplate)
    CouponApplicationService->>CouponRepository: softDeleteAllByTemplateId(couponTemplateId)
    CouponApplicationService-->>CouponAdminV1Controller: void
```
