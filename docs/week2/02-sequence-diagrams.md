# 02. 시퀀스 다이어그램

---

## SD-01. 주문 생성

재고 확인 및 차감, 부분 주문 처리, 스냅샷 저장의 책임 분리와 트랜잭션 경계를 검증한다.

```mermaid
sequenceDiagram
    participant User
    participant OrderController
    participant OrderFacade
    participant OrderService
    participant StockService
    participant OrderRepository
    participant OrderItemSnapshotRepository

    User->>OrderController: POST /api/v1/orders
    OrderController->>OrderFacade: createOrder(userId, items)

    loop 각 주문 항목
        OrderFacade->>StockService: deductIfAvailable(productId, quantity)

        alt 재고 충분
            StockService-->>OrderFacade: ORDERED
        else 재고 부족 또는 상품 없음
            StockService-->>OrderFacade: SKIPPED
        end
    end

    alt ORDERED 항목 없음
        OrderFacade-->>OrderController: 400 Bad Request
    else ORDERED 항목 있음
        OrderFacade->>OrderService: createOrder(userId, orderedItems, skippedItems)
        OrderService->>OrderRepository: save(order, orderItems)
        OrderService->>OrderItemSnapshotRepository: save(productName, price, brandName)
        OrderService-->>OrderFacade: orderResult
        OrderFacade-->>OrderController: { orderedItems, skippedItems }
        OrderController-->>User: 200 OK
    end
```

**읽는 포인트**
- ORDERED/SKIPPED 분류는 Facade에서 결정하고, OrderService는 저장만 담당한다.
- 재고 차감과 주문 저장은 단일 트랜잭션으로 처리된다.
- 스냅샷은 OrderService 내부에서 주문 저장 직후 동일 트랜잭션 안에 저장된다.

---

## SD-02. 좋아요 등록 (멱등 처리)

좋아요 중복 요청 시 에러 없이 처리되는 멱등성 흐름과 like_count 책임 위치를 검증한다.

```mermaid
sequenceDiagram
    participant User
    participant LikeController
    participant LikeFacade
    participant LikeService
    participant LikeRepository

    User->>LikeController: POST /api/v1/products/{productId}/likes
    LikeController->>LikeFacade: addLike(userId, productId)
    LikeFacade->>LikeService: addLike(userId, productId)
    LikeService->>LikeRepository: exists(userId, productId)

    alt 이미 좋아요한 경우
        LikeRepository-->>LikeService: true
        LikeService-->>LikeFacade: 200 OK (무시)
    else 좋아요 없음
        LikeService->>LikeRepository: save(like)
        LikeService->>LikeRepository: incrementLikeCount(productId)
        LikeService-->>LikeFacade: 200 OK
    end

    LikeFacade-->>LikeController: 200 OK
    LikeController-->>User: 200 OK
```

**읽는 포인트**
- 이미 좋아요가 존재하면 저장 없이 200 OK를 반환한다. like_count는 변경되지 않는다.
- like_count 증감 책임은 LikeService가 가지며, Product 도메인에 위임한다.

---

## SD-03. 브랜드 삭제 (소프트 딜리트 cascade)

브랜드 삭제 시 상품 cascade 처리가 애플리케이션 레벨에서 일어남을 검증한다.

```mermaid
sequenceDiagram
    participant Admin
    participant BrandController
    participant BrandFacade
    participant BrandService
    participant ProductService
    participant BrandRepository
    participant ProductRepository

    Admin->>BrandController: DELETE /api-admin/v1/brands/{brandId}
    BrandController->>BrandFacade: deleteBrand(brandId)
    BrandFacade->>BrandService: deleteBrand(brandId)
    BrandService->>BrandRepository: findById(brandId)

    alt 브랜드 없음
        BrandRepository-->>BrandService: null
        BrandService-->>BrandFacade: 404 Not Found
        BrandFacade-->>BrandController: 404 Not Found
        BrandController-->>Admin: 404 Not Found
    else 브랜드 존재
        BrandService->>BrandRepository: softDelete(brandId)
        BrandService->>ProductService: softDeleteByBrandId(brandId)
        ProductService->>ProductRepository: softDeleteAllByBrandId(brandId)
        BrandService-->>BrandFacade: 200 OK
        BrandFacade-->>BrandController: 200 OK
        BrandController-->>Admin: 200 OK
    end
```

**읽는 포인트**
- 실제 행 삭제 없이 `deleted_at` 설정으로 처리하므로 기존 주문 스냅샷에 영향이 없다.
- cascade는 DB ON DELETE가 아닌 BrandService → ProductService 호출로 처리한다.
