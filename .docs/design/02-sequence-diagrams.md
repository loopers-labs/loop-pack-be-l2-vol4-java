# 02. 시퀀스 다이어그램

> Mermaid `sequenceDiagram` 기반. 책임 객체(Controller / Facade / DomainService / Repository / 외부 시스템)가 드러나도록 작성.
> 계층: `interfaces` → `application`(Facade) → `domain`(Service) → `infrastructure`(Repository).

## 다이어그램 1. 주문 생성 + 결제

> 재고 차감 → 주문 생성 → 외부 PG 결제(실결제액). 포인트는 결제 금액을 깎는 할인 수단. 실패(재고 부족·포인트 범위 오류·PG 실패) 흐름까지 표현.

```mermaid
sequenceDiagram
    autonumber
    actor User as 고객
    participant API as OrderV1Controller
    participant Facade as OrderFacade
    participant OrderSvc as OrderService
    participant ProductSvc as ProductService
    participant PointSvc as PointService
    participant PaySvc as PaymentService
    participant PG as 외부 PG

    User->>API: POST /api/v1/orders {items, usePoint}
    API->>Facade: order(userId, items, usePoint)

    Facade->>ProductSvc: 재고 확인·차감(items)
    alt 재고 부족
        ProductSvc-->>Facade: 재고 부족 예외
        Facade-->>API: CoreException(BAD_REQUEST)
        API-->>User: 400 Bad Request
    end
    ProductSvc-->>Facade: 차감 완료 + 상품 스냅샷(상품명·단가)
    Note over Facade,OrderSvc: 주문 총액 = Σ(스냅샷 단가 × 수량) — 이후 상품 가격이 바뀌어도 영향 없음

    Facade->>PointSvc: 사용 포인트 검증(userId, usePoint, 주문 총액)
    alt usePoint 범위 오류 (보유 초과 또는 총액 초과)
        PointSvc-->>Facade: 검증 실패 예외
        Facade->>ProductSvc: 재고 원복
        Facade-->>API: CoreException(BAD_REQUEST)
        API-->>User: 400 Bad Request
    end
    PointSvc-->>Facade: 검증 통과

    Facade->>OrderSvc: 주문 생성(PENDING, 스냅샷, totalAmount, usePoint)
    OrderSvc-->>Facade: order(PENDING)

    Note over Facade,PG: 실결제액 = 주문 총액 − usePoint. 실결제액이 0이면 PG 호출 생략 후 바로 PAID
    Facade->>PaySvc: 결제 생성(PENDING, 실결제액)
    PaySvc->>PG: 결제 요청(orderId, 실결제액)
    alt PG 결제 성공
        PG-->>PaySvc: 승인(externalTxId)
        PaySvc-->>Facade: Payment SUCCESS
        Facade->>PointSvc: 포인트 차감 확정(usePoint)
        Facade->>OrderSvc: 주문 PAID
        Facade-->>API: OrderInfo
        API-->>User: 200 OK · ApiResponse(OrderResponse)
    else PG 결제 실패 / 타임아웃
        PG-->>PaySvc: 거절 / 타임아웃
        PaySvc-->>Facade: Payment FAILED
        Facade->>ProductSvc: 재고 원복
        Facade->>OrderSvc: 주문 FAILED
        Facade-->>API: CoreException(결제 실패)
        API-->>User: 4xx · 결제 실패
    end
```

- 트랜잭션 경계: 재고·주문·포인트 갱신은 로컬 DB 트랜잭션, 외부 PG 호출은 트랜잭션 밖에서 수행한다. PG 결과를 받은 뒤 성공이면 포인트 차감·주문 확정을, 실패면 재고 원복을 처리한다.

---

## 다이어그램 2. 상품 좋아요 등록 / 취소 (멱등)

> POST·DELETE 양쪽 모두 멱등 보장. 이미 좋아요 / 좋아요 안 한 상태의 처리 분기를 표현.

```mermaid
sequenceDiagram
    autonumber
    actor User as 고객
    participant API as LikeV1Controller
    participant Facade as LikeFacade
    participant LikeSvc as LikeService
    participant Repo as LikeRepository

    Note over User,Repo: 좋아요 등록 (멱등)
    User->>API: POST /api/v1/products/{productId}/likes
    API->>Facade: like(userId, productId)
    Facade->>LikeSvc: register(userId, productId)
    Note over LikeSvc,Repo: 존재 확인 + 저장은 단일 트랜잭션 / UNIQUE(user_id, product_id)가 멱등의 최종 방어선
    LikeSvc->>Repo: existsByUserIdAndProductId(userId, productId)
    alt 이미 좋아요함
        Repo-->>LikeSvc: true
        LikeSvc-->>Facade: noop (1건 유지)
    else 좋아요 안 함
        Repo-->>LikeSvc: false
        LikeSvc->>Repo: save(like)
        Repo-->>LikeSvc: 저장 완료
    end
    Facade-->>API: 완료
    API-->>User: 200 OK

    Note over User,Repo: 좋아요 취소 (멱등)
    User->>API: DELETE /api/v1/products/{productId}/likes
    API->>Facade: unlike(userId, productId)
    Facade->>LikeSvc: cancel(userId, productId)
    LikeSvc->>Repo: deleteByUserIdAndProductId(userId, productId)
    Note right of Repo: 대상 행이 없어도 에러 없이 0건 유지
    Repo-->>LikeSvc: 완료
    Facade-->>API: 완료
    API-->>User: 200 OK
```

---

## 다이어그램 3. 상품 목록 조회 (정렬·필터·페이징)

> 정렬 기준별 쿼리 분기, `likes_desc`는 좋아요 집계 기준.

```mermaid
sequenceDiagram
    autonumber
    actor User as 고객
    participant API as ProductV1Controller
    participant Facade as ProductFacade
    participant ProductSvc as ProductService
    participant Repo as ProductRepository

    User->>API: GET /api/v1/products?brandId&sort&page&size
    API->>Facade: getProducts(brandId, sort, pageable)
    Facade->>ProductSvc: search(brandId, sort, pageable)
    alt sort = latest (기본)
        ProductSvc->>Repo: findPage(brandId, ORDER BY created_at DESC)
    else sort = price_asc
        ProductSvc->>Repo: findPage(brandId, ORDER BY price ASC)
    else sort = likes_desc
        ProductSvc->>Repo: findPage(brandId, likes JOIN · GROUP BY · ORDER BY COUNT DESC)
    end
    Repo-->>ProductSvc: Page(Product)
    Note over ProductSvc,Repo: 좋아요 수는 현재 페이지의 상품 ID에 대해서만 단일 집계 쿼리로 조회 (N+1 금지)
    ProductSvc->>Repo: 좋아요 수 집계(productIds)
    Repo-->>ProductSvc: productId→likeCount
    ProductSvc-->>Facade: Page(ProductInfo)
    Facade-->>API: Page(ProductInfo)
    API-->>User: 200 OK · ApiResponse(Page(ProductResponse))
```

---

## 다이어그램 4. 주문 단건 조회 (본인 권한 체크)

> 본인 주문만 조회 가능. 타인 주문은 존재를 노출하지 않기 위해 404.

```mermaid
sequenceDiagram
    autonumber
    actor User as 고객
    participant API as OrderV1Controller
    participant Facade as OrderFacade
    participant OrderSvc as OrderService
    participant Repo as OrderRepository

    User->>API: GET /api/v1/orders/{orderId}
    API->>Facade: getOrder(userId, orderId)
    Facade->>OrderSvc: getOrder(orderId)
    OrderSvc->>Repo: findById(orderId)
    alt 주문 없음
        Repo-->>OrderSvc: Optional.empty
        OrderSvc-->>Facade: CoreException(NOT_FOUND)
        Facade-->>API: 404
        API-->>User: 404 Not Found
    else 주문 존재
        Repo-->>OrderSvc: order
        OrderSvc-->>Facade: order
        Facade->>Facade: order.userId == 요청 userId 검증
        alt 본인 주문 아님
            Facade-->>API: CoreException(NOT_FOUND) · 존재 노출 차단
            API-->>User: 404 Not Found
        else 본인 주문
            Facade-->>API: OrderInfo
            API-->>User: 200 OK · ApiResponse(OrderResponse)
        end
    end
```

---

## 다이어그램 5. 브랜드 삭제 (어드민) — 소속 상품 cascade 삭제

> 어드민 인증 헤더로 식별. 브랜드 삭제 시 소속 상품도 함께 삭제된다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 어드민
    participant API as BrandAdminV1Controller
    participant Facade as BrandFacade
    participant BrandSvc as BrandService
    participant ProductSvc as ProductService

    Admin->>API: DELETE /api-admin/v1/brands/{brandId}
    API->>Facade: deleteBrand(brandId)
    Facade->>BrandSvc: getBrand(brandId)
    alt 브랜드 없음
        BrandSvc-->>Facade: CoreException(NOT_FOUND)
        Facade-->>API: 404
        API-->>Admin: 404 Not Found
    else 브랜드 존재
        BrandSvc-->>Facade: brand
        Facade->>ProductSvc: 소속 상품 전체 삭제(brandId)
        ProductSvc-->>Facade: 삭제 완료
        Facade->>BrandSvc: 브랜드 삭제(brandId)
        BrandSvc-->>Facade: 삭제 완료
        Facade-->>API: 완료
        API-->>Admin: 200 OK
    end
    Note over Facade,ProductSvc: 상품 삭제 + 브랜드 삭제는 단일 트랜잭션. 대량 상품의 배치 삭제는 후속 과제(동시성·대용량) 범위
```

## ✅ 과제 체크리스트 (이 문서 관점)

- [x] 시퀀스 다이어그램이 최소 2개 이상인가? (총 5개)
- [x] 시퀀스 다이어그램에서 책임 객체가 드러나는가?
- [x] 실패/예외 흐름이 표현되어 있는가? (재고 부족·포인트 부족·PG 실패·권한)
