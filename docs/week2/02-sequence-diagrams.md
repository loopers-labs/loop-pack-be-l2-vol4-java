# 시퀀스 다이어그램

## 1. 좋아요 등록

인증 → 상품 존재 여부 → 중복 여부 순서로 책임이 분리되는지, 각 예외 처리가 어느 레이어에서 발생하는지 확인한다.

```mermaid
sequenceDiagram
    participant Client
    participant LikeController
    participant AuthUserArgumentResolver
    participant LikeService
    participant ProductRepository
    participant LikeRepository

    Client->>LikeController: POST /api/v1/products/{productId}/likes
    Note over LikeController: X-Loopers-LoginId, X-Loopers-LoginPw 헤더

    LikeController->>AuthUserArgumentResolver: @AuthUser 파라미터 resolve
    AuthUserArgumentResolver->>AuthUserArgumentResolver: authenticate(loginId, loginPw)
    AuthUserArgumentResolver-->>LikeController: AuthUserContext

    LikeController->>LikeService: like(userId, productId)

    LikeService->>ProductRepository: findById(productId)
    alt 상품이 없거나 soft delete된 경우
        ProductRepository-->>LikeService: Optional.empty()
        LikeService-->>LikeController: throw NOT_FOUND
        LikeController-->>Client: 404 Not Found
    end
    ProductRepository-->>LikeService: Product

    LikeService->>LikeRepository: existsByUserIdAndProductId(userId, productId)
    alt 이미 좋아요한 경우
        LikeRepository-->>LikeService: true
        LikeService-->>LikeController: throw CONFLICT
        LikeController-->>Client: 409 Conflict
    end

    LikeService->>LikeRepository: save(like)
    LikeService-->>LikeController: 완료
    LikeController-->>Client: 201 Created
```

**읽는 포인트**
- soft delete된 상품은 `findById`에서 `deleted_at IS NULL` 조건으로 걸러진다.
- 중복 좋아요는 DB UK 제약보다 앞서 애플리케이션 레벨에서 먼저 확인한다.
- 인증은 `AuthUserArgumentResolver`에서 완결되어 Controller에는 `AuthUserContext`만 전달된다.

---

## 2. 주문 생성

단일 트랜잭션 범위, 재고 차감 실패 시 전체 롤백 흐름, 스냅샷 저장 시점을 확인한다.

```mermaid
sequenceDiagram
    participant Client
    participant OrderController
    participant AuthUserArgumentResolver
    participant OrderService
    participant Product
    participant ProductRepository
    participant OrderRepository

    Client->>OrderController: POST /api/v1/orders
    Note over OrderController: { items: [{productId, quantity}, ...] }

    OrderController->>AuthUserArgumentResolver: @AuthUser 파라미터 resolve
    AuthUserArgumentResolver-->>OrderController: AuthUserContext

    OrderController->>OrderService: createOrder(userId, items)
    Note over OrderService: @Transactional 시작

    loop 각 주문 항목
        OrderService->>ProductRepository: findById(productId)
        alt 상품 없음 or soft delete됨
            ProductRepository-->>OrderService: Optional.empty()
            OrderService-->>OrderController: throw NOT_FOUND
            Note over OrderService: 트랜잭션 롤백
            OrderController-->>Client: 404 Not Found
        end
        ProductRepository-->>OrderService: Product

        OrderService->>Product: deductStock(quantity)
        alt 재고 부족
            Product-->>OrderService: throw BAD_REQUEST
            OrderService-->>OrderController: throw BAD_REQUEST
            Note over OrderService: 트랜잭션 롤백
            OrderController-->>Client: 400 Bad Request
        end

        OrderService->>ProductRepository: save(product)
        Note over OrderService: OrderItem 생성 (상품명, 가격 스냅샷)
    end

    OrderService->>OrderRepository: save(order)
    Note over OrderService: @Transactional 종료

    OrderService-->>OrderController: Order
    OrderController-->>Client: 201 Created
```

**읽는 포인트**
- `@Transactional`이 루프 전체를 감싸므로 중간 실패 시 이미 차감된 재고도 전부 롤백된다.
- 재고 차감 책임은 `OrderService`가 아닌 `Product.deductStock()`에 있다.
- `OrderItem` 생성 시점에 상품명과 가격을 직접 복사해두므로, 이후 상품 정보가 바뀌어도 주문 내역은 영향받지 않는다.

---

## 3. 브랜드 삭제

브랜드 삭제 시 연관 상품도 soft delete 처리되는 흐름, 트랜잭션 범위를 확인한다.

```mermaid
sequenceDiagram
    participant Admin
    participant BrandController
    participant BrandService
    participant BrandRepository
    participant ProductRepository

    Admin->>BrandController: DELETE /api-admin/v1/brands/{brandId}
    Note over BrandController: X-Loopers-Ldap 헤더

    BrandController->>BrandService: deleteBrand(brandId)
    Note over BrandService: @Transactional 시작

    BrandService->>BrandRepository: findById(brandId)
    alt 브랜드 없음
        BrandRepository-->>BrandService: Optional.empty()
        BrandService-->>BrandController: throw NOT_FOUND
        BrandController-->>Admin: 404 Not Found
    end
    BrandRepository-->>BrandService: Brand

    BrandService->>ProductRepository: findAllByBrandId(brandId)
    ProductRepository-->>BrandService: List~Product~

    loop 각 상품
        BrandService->>Product: delete()
        Note over Product: deleted_at 채움
        BrandService->>ProductRepository: save(product)
    end

    BrandService->>Brand: delete()
    Note over Brand: deleted_at 채움
    BrandService->>BrandRepository: save(brand)

    Note over BrandService: @Transactional 종료
    BrandService-->>BrandController: 완료
    BrandController-->>Admin: 200 OK
```

**읽는 포인트**
- 상품 soft delete → 브랜드 soft delete 순서로 처리된다.
- 하나의 트랜잭션 안에서 처리되므로 중간 실패 시 전체 롤백, 부분 삭제 상태가 발생하지 않는다.
- `deleted_at`을 채우는 책임은 `Brand.delete()`, `Product.delete()` 도메인 메서드에 있다.
