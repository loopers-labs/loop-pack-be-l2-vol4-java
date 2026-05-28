# 02. 시퀀스 다이어그램

## 1. 상품 목록 조회 시퀀스

### 목적

사용자가 브랜드 필터, 정렬 조건, 페이지 정보를 지정하여 상품 목록을 조회하는 흐름을 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor User
    participant ProductController
    participant ProductService
    participant ProductRepository
    participant DB

    User->>ProductController: GET /api/v1/products?brandId=&sort=&page=&size=
    ProductController->>ProductService: 상품 목록 조회 요청(brandId, sort, page, size)
    ProductService->>ProductRepository: 조건에 맞는 상품 목록 조회(brandId, sort, page, size)
    ProductRepository->>DB: SELECT * FROM products WHERE ... ORDER BY ... LIMIT ? OFFSET ?
    DB-->>ProductRepository: 상품 목록
    ProductRepository-->>ProductService: 상품 목록 반환
    ProductService-->>ProductController: 상품 목록 반환
    ProductController-->>User: 200 OK (상품 목록, 빈 배열 포함)
```

---

## 2. 상품 상세 조회 시퀀스

### 목적

사용자가 상품 목록에서 특정 상품을 선택했을 때, 상품 상세 정보가 어떻게 조회되어 반환되는지 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor User
    participant ProductController
    participant ProductService
    participant ProductRepository
    participant DB

    User->>ProductController: GET /api/v1/products/{productId}
    ProductController->>ProductService: 상품 조회 요청(productId)
    ProductService->>ProductRepository: findById(productId)
    ProductRepository->>DB: SELECT * FROM products WHERE id = ?
    DB-->>ProductRepository: 상품 데이터
    ProductRepository-->>ProductService: Product 반환

    alt 상품이 존재하지 않는 경우
        ProductService-->>ProductController: 상품 없음 예외
        ProductController-->>User: 404 Not Found
    else 상품이 존재하는 경우
        ProductService-->>ProductController: 상품 정보 반환
        ProductController-->>User: 200 OK (상품 상세)
    end
```

---

## 3. 브랜드 조회 시퀀스

### 목적

사용자가 브랜드 ID로 브랜드 상세 정보를 조회하는 흐름을 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor User
    participant BrandController
    participant BrandService
    participant BrandRepository
    participant DB

    User->>BrandController: GET /api/v1/brands/{brandId}
    BrandController->>BrandService: 브랜드 조회 요청(brandId)
    BrandService->>BrandRepository: findById(brandId)
    BrandRepository->>DB: SELECT * FROM brands WHERE id = ?
    DB-->>BrandRepository: 조회 결과
    BrandRepository-->>BrandService: 조회 결과 반환

    alt 브랜드가 존재하지 않는 경우
        BrandService-->>BrandController: 브랜드 없음 예외
        BrandController-->>User: 404 Not Found
    else 브랜드가 존재하는 경우
        BrandService-->>BrandController: 브랜드 정보 반환
        BrandController-->>User: 200 OK (브랜드 상세)
    end
```

---

## 4. 상품 좋아요 등록 시퀀스

### 목적

사용자가 상품에 좋아요를 등록할 때, 중복 등록 없이 멱등하게 처리되는 흐름을 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor User
    participant LikeController
    participant LikeService
    participant ProductService
    participant ProductRepository
    participant LikeRepository
    participant DB

    User->>LikeController: POST /api/v1/products/{productId}/likes
    LikeController->>LikeService: 좋아요 등록 요청(userId, productId)

    LikeService->>ProductService: 상품 존재 확인(productId)
    ProductService->>ProductRepository: findById(productId)
    ProductRepository->>DB: SELECT * FROM products WHERE id = ?
    DB-->>ProductRepository: 조회 결과
    ProductRepository-->>ProductService: 조회 결과 반환

    alt 상품이 존재하지 않는 경우
        ProductService-->>LikeService: 상품 없음 예외
        LikeService-->>LikeController: 상품 없음 예외 전파
        LikeController-->>User: 404 Not Found
    else 상품이 존재하는 경우
        ProductService-->>LikeService: Product 반환
        LikeService->>LikeRepository: 좋아요 등록 여부 확인(userId, productId)
        LikeRepository->>DB: SELECT * FROM product_likes WHERE user_id=? AND product_id=?
        DB-->>LikeRepository: 조회 결과
        LikeRepository-->>LikeService: 조회 결과 반환

        alt 이미 좋아요가 등록된 경우
            LikeService-->>LikeController: 멱등 처리 (중복 등록 없음)
            LikeController-->>User: 200 OK
        else 좋아요 미등록 상태
            LikeService->>LikeRepository: 좋아요 저장(userId, productId)
            LikeRepository->>DB: INSERT INTO product_likes ...
            DB-->>LikeRepository: 저장 완료
            LikeRepository-->>LikeService: 저장 완료
            LikeService-->>LikeController: 등록 완료
            LikeController-->>User: 200 OK
        end
    end
```

---

## 5. 상품 좋아요 취소 시퀀스

### 목적

사용자가 좋아요를 취소할 때, 좋아요가 없는 상태에서도 오류 없이 멱등하게 처리되는 흐름을 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor User
    participant LikeController
    participant LikeService
    participant LikeRepository
    participant DB

    User->>LikeController: DELETE /api/v1/products/{productId}/likes
    LikeController->>LikeService: 좋아요 취소 요청(userId, productId)
    LikeService->>LikeRepository: 좋아요 조회(userId, productId)
    LikeRepository->>DB: SELECT * FROM product_likes WHERE user_id=? AND product_id=?
    DB-->>LikeRepository: 조회 결과
    LikeRepository-->>LikeService: 조회 결과 반환

    alt 좋아요가 등록된 경우
        LikeService->>LikeRepository: 좋아요 삭제(userId, productId)
        LikeRepository->>DB: DELETE FROM product_likes WHERE user_id=? AND product_id=?
        DB-->>LikeRepository: 삭제 완료
        LikeRepository-->>LikeService: 삭제 완료
    end

    LikeService-->>LikeController: 완료
    LikeController-->>User: 200 OK
```

---

## 6. 주문 생성 시퀀스

### 목적

사용자가 여러 상품을 한 번에 주문 요청할 때, 재고 차감 → 포인트 차감 → 결제 요청 순서로 처리되는 흐름과 결제 실패 시 보상 처리를 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor User
    participant OrderController
    participant OrderService
    participant ProductService
    participant PointService
    participant OrderRepository
    participant PaymentGateway
    participant DB

    User->>OrderController: POST /api/v1/orders
    OrderController->>OrderService: 주문 생성 요청(userId, items)

    OrderService->>ProductService: 상품 목록 및 재고 조회 요청
    ProductService-->>OrderService: 상품 목록 반환

    alt 재고 부족 상품이 하나라도 있는 경우
        OrderService-->>OrderController: 재고 부족 예외
        OrderController-->>User: 400 Bad Request
    else 모든 상품의 재고가 충분한 경우
        OrderService->>ProductService: 재고 차감 요청(items)
        ProductService-->>OrderService: 차감 완료

        OrderService->>PointService: 포인트 차감 요청(userId, amount)
        PointService-->>OrderService: 차감 완료

        OrderService->>OrderRepository: 주문 저장 (PENDING, 상품명·가격 스냅샷)
        OrderRepository->>DB: INSERT INTO orders, order_items
        DB-->>OrderRepository: 저장 완료
        OrderRepository-->>OrderService: 주문 정보 반환

        OrderService->>PaymentGateway: 결제 요청(orderId, amount)
        PaymentGateway-->>OrderService: 결제 결과

        alt 결제 실패
            OrderService->>PointService: 포인트 복구(userId, amount)
            PointService-->>OrderService: 복구 완료
            OrderService->>ProductService: 재고 복구(items)
            ProductService-->>OrderService: 복구 완료
            OrderService->>OrderRepository: 주문 상태 FAILED 업데이트
            OrderRepository->>DB: UPDATE orders SET status = 'FAILED'
            DB-->>OrderRepository: 완료
            OrderService-->>OrderController: 결제 실패 예외
            OrderController-->>User: 400 Bad Request
        else 결제 성공
            OrderService->>OrderRepository: 주문 상태 PAID 업데이트
            OrderRepository->>DB: UPDATE orders SET status = 'PAID'
            DB-->>OrderRepository: 완료
            OrderRepository-->>OrderService: 완료
            OrderService-->>OrderController: 주문 생성 완료
            OrderController-->>User: 201 Created
        end
    end
```

---

## 7. 주문 목록 조회 시퀀스

### 목적

사용자가 기간 조건으로 자신의 주문 목록을 조회하는 흐름을 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor User
    participant OrderController
    participant OrderService
    participant OrderRepository
    participant DB

    User->>OrderController: GET /api/v1/orders?startAt=2026-01-01&endAt=2026-01-31
    OrderController->>OrderService: 주문 목록 조회 요청(userId, startAt, endAt)
    OrderService->>OrderRepository: 사용자 + 기간 조건으로 주문 조회
    OrderRepository->>DB: SELECT * FROM orders WHERE user_id=? AND ordered_at BETWEEN ? AND ?
    DB-->>OrderRepository: 주문 목록
    OrderRepository-->>OrderService: 주문 목록 반환
    OrderService-->>OrderController: 주문 목록 반환
    OrderController-->>User: 200 OK
```

---

## 8. 주문 상세 조회 시퀀스

### 목적

사용자가 특정 주문의 상세 정보를 조회할 때, 본인 소유 여부를 확인하는 흐름을 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor User
    participant OrderController
    participant OrderService
    participant OrderRepository
    participant DB

    User->>OrderController: GET /api/v1/orders/{orderId}
    OrderController->>OrderService: 주문 상세 조회 요청(userId, orderId)
    OrderService->>OrderRepository: 주문 조회(orderId)
    OrderRepository->>DB: SELECT orders, order_items WHERE orders.id = ?
    DB-->>OrderRepository: 주문 및 주문 항목 데이터
    OrderRepository-->>OrderService: 조회 결과 반환

    alt 주문이 존재하지 않는 경우
        OrderService-->>OrderController: 주문 없음 예외
        OrderController-->>User: 404 Not Found
    else 타인의 주문에 접근하는 경우
        OrderService-->>OrderController: 접근 불가 예외
        OrderController-->>User: 403 Forbidden
    else 본인의 주문인 경우
        OrderService-->>OrderController: 주문 상세 반환
        OrderController-->>User: 200 OK
    end
```

---

## 9. 어드민 주문 목록 조회 시퀀스

### 목적

어드민이 페이지네이션으로 전체 주문 목록을 조회하는 흐름을 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor Admin
    participant OrderController
    participant OrderService
    participant OrderRepository
    participant DB

    Admin->>OrderController: GET /api-admin/v1/orders?page=0&size=20
    Note over Admin,OrderController: Header: X-Loopers-Ldap: loopers.admin
    OrderController->>OrderService: 주문 목록 조회 요청(page, size)
    OrderService->>OrderRepository: 전체 주문 페이지 조회(page, size)
    OrderRepository->>DB: SELECT * FROM orders ORDER BY created_at DESC LIMIT ? OFFSET ?
    DB-->>OrderRepository: 주문 목록
    OrderRepository-->>OrderService: 주문 목록 반환
    OrderService-->>OrderController: 주문 목록 반환
    OrderController-->>Admin: 200 OK (주문 목록)
```

---

## 10. 어드민 주문 상세 조회 시퀀스

### 목적

어드민이 특정 주문의 상세 정보를 조회하는 흐름을 나타낸다.

### 다이어그램

```mermaid
sequenceDiagram
    actor Admin
    participant OrderController
    participant OrderService
    participant OrderRepository
    participant DB

    Admin->>OrderController: GET /api-admin/v1/orders/{orderId}
    Note over Admin,OrderController: Header: X-Loopers-Ldap: loopers.admin
    OrderController->>OrderService: 주문 상세 조회 요청(orderId)
    OrderService->>OrderRepository: 주문 조회(orderId)
    OrderRepository->>DB: SELECT orders, order_items WHERE orders.id = ?
    DB-->>OrderRepository: 주문 및 주문 항목 데이터
    OrderRepository-->>OrderService: 조회 결과 반환

    alt 주문이 존재하지 않는 경우
        OrderService-->>OrderController: 주문 없음 예외
        OrderController-->>Admin: 404 Not Found
    else 주문이 존재하는 경우
        OrderService-->>OrderController: 주문 상세 반환
        OrderController-->>Admin: 200 OK (주문 상세)
    end
```
