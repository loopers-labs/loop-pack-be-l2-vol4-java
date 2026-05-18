# 시퀀스 다이어그램

## 1. 좋아요 등록

**검증할 것**: 인증 → 상품 존재 여부 → 중복 여부 순서가 맞는지, 좋아요 등록의 책임 분리가 적절한지.

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
    AuthUserArgumentResolver-->>LikeController: AuthUserContext(loginId)

    LikeController->>LikeService: like(userId, productId)

    LikeService->>ProductRepository: findById(productId)
    alt 상품이 존재하지 않는 경우
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
    LikeRepository-->>LikeService: false

    LikeService->>LikeRepository: save(like)
    LikeRepository-->>LikeService: Like

    LikeService-->>LikeController: 완료
    LikeController-->>Client: 201 Created
```

---

## 2. 주문 생성

**검증할 것**: 재고 확인과 차감이 하나의 트랜잭션 안에서 처리되는지, 스냅샷 저장 시점이 언제인지.

```mermaid
sequenceDiagram
    participant Client
    participant OrderController
    participant AuthUserArgumentResolver
    participant OrderService
    participant ProductRepository
    participant OrderRepository

    Client->>OrderController: POST /api/v1/orders
    Note over OrderController: { items: [{productId, quantity}, ...] }

    OrderController->>AuthUserArgumentResolver: @AuthUser 파라미터 resolve
    AuthUserArgumentResolver-->>OrderController: AuthUserContext(loginId)

    OrderController->>OrderService: createOrder(userId, items)

    loop 각 주문 항목에 대해
        OrderService->>ProductRepository: findById(productId)
        alt 상품이 존재하지 않는 경우
            ProductRepository-->>OrderService: Optional.empty()
            OrderService-->>OrderController: throw NOT_FOUND
            OrderController-->>Client: 404 Not Found
        end
        ProductRepository-->>OrderService: Product

        OrderService->>Product: deductStock(quantity)
        alt 재고 부족
            Product-->>OrderService: throw BAD_REQUEST
            OrderService-->>OrderController: throw BAD_REQUEST
            OrderController-->>Client: 400 Bad Request
        end
        Product-->>OrderService: 재고 차감 완료
        OrderService->>ProductRepository: save(product)

        Note over OrderService: OrderItem 생성 (상품명, 가격 스냅샷 포함)
    end

    OrderService->>OrderRepository: save(order)
    OrderRepository-->>OrderService: Order

    OrderService-->>OrderController: Order
    OrderController-->>Client: 201 Created
```

---

## 3. 상품 목록 조회

**검증할 것**: 정렬/필터 조건이 어느 레이어에서 처리되는지, 좋아요 수 정렬 시 어떤 방식으로 집계하는지.

```mermaid
sequenceDiagram
    participant Client
    participant ProductController
    participant ProductService
    participant ProductRepository

    Client->>ProductController: GET /api/v1/products?brandId=1&sort=latest&page=0&size=20

    ProductController->>ProductService: getProducts(brandId, sort, page, size)
    ProductService->>ProductRepository: findAll(filter, pageable)
    ProductRepository-->>ProductService: List~Product~
    ProductService-->>ProductController: List~ProductInfo~
    ProductController-->>Client: 200 OK
```

---

## 설계 고민

**주문 생성 트랜잭션 범위**

재고 확인과 차감이 같은 트랜잭션 안에 있어야 한다.
여러 상품에 대해 루프를 돌면서 차감하므로, 중간에 하나라도 실패하면 전체 롤백이 보장되어야 한다.

**좋아요 수 기반 정렬**

`likes_desc` 정렬 시 상품별 좋아요 수를 집계해야 한다.
현재 설계에서는 `likes` 테이블 COUNT JOIN으로 처리.
상품 수가 많아지면 성능 이슈가 될 수 있어, 추후 `products.like_count` 캐시 컬럼 도입을 고려할 수 있다.
