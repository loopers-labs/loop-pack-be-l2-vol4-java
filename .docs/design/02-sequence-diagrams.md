# 02. Sequence Diagrams — 시퀀스 다이어그램

전체 시나리오를 다룬다.

---

## 시나리오 1. 상품 목록 조회

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: GET /api/v1/products?sort=latest&brandId=1&page=0
    Controller->>+Facade: getProducts(sort, brandId, page, size)
    Facade->>+DB: SELECT products (filter + sort + pagination)
    DB-->>-Facade: product list
    Facade->>DB: SELECT stocks WHERE productId IN (...)
    DB-->>Facade: stock list
    deactivate Facade
    Controller-->>-Client: 200
```

---

## 시나리오 2. 상품 상세 조회

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: GET /api/v1/products/{productId}
    Controller->>+Facade: getProduct(productId)
    Facade->>+DB: SELECT product WHERE id = productId
    DB-->>-Facade: product | null

    alt 상품 없음 또는 삭제됨
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Client: 404
    else 상품 존재
        Facade->>DB: SELECT stock WHERE productId = productId
        DB-->>Facade: stock
        Controller-->>Client: 200
    end
    deactivate Facade
    deactivate Controller
```

---

## 시나리오 3. 브랜드 정보 조회

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: GET /api/v1/brands/{brandId}
    Controller->>+Facade: getBrand(brandId)
    Facade->>+DB: SELECT brand WHERE id = brandId
    DB-->>-Facade: brand | null

    alt 브랜드 없음 또는 삭제됨
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Client: 404
    else 브랜드 존재
        Controller-->>Client: 200
    end
    deactivate Facade
    deactivate Controller
```

---

## 시나리오 4. 좋아요

### 4-1. 좋아요 등록

상품 존재 확인 후 중복 확인 순으로 검증한다. 존재하지 않는 상품에 대한 중복 여부를 묻는 것은 의미 없으므로 순서가 중요하다.

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: POST /api/v1/products/{productId}/likes
    Controller->>+Facade: addLike(userId, productId)

    Facade->>+DB: SELECT product WHERE id = productId
    DB-->>-Facade: product | null

    alt 상품 없음 또는 삭제됨
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Client: 404
    else 상품 존재
        Facade->>DB: SELECT like WHERE userId AND productId
        DB-->>Facade: like | null

        alt 이미 좋아요한 상품
            Facade-->>Controller: CoreException(CONFLICT)
            Controller-->>Client: 409
        else 좋아요 없음
            Note over Facade: LikeService.createLike() → LikeModel 생성
            Facade->>DB: INSERT like
            Facade->>DB: UPDATE products SET like_count = like_count + 1
            Controller-->>Client: 200
        end
    end
    deactivate Facade
    deactivate Controller
```

### 4-2. 좋아요 취소

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: DELETE /api/v1/products/{productId}/likes
    Controller->>+Facade: cancelLike(userId, productId)

    Facade->>DB: SELECT like WHERE userId AND productId
    DB-->>Facade: like | null

    alt 좋아요 없음
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Client: 404
    else 좋아요 존재
        Facade->>DB: DELETE like
        Facade->>DB: UPDATE products SET like_count = like_count - 1
        Controller-->>Client: 200
    end
    deactivate Facade
    deactivate Controller
```

### 4-3. 내 좋아요 목록 조회

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: GET /api/v1/users/{userId}/likes
    Controller->>+Facade: getLikedProducts(userId)
    Facade->>DB: SELECT likes WHERE userId
    DB-->>Facade: like list
    Facade->>DB: SELECT products WHERE id IN (productIds)
    DB-->>Facade: product list
    Facade->>DB: SELECT stocks WHERE productId IN (productIds)
    DB-->>Facade: stock list
    deactivate Facade
    Controller-->>-Client: 200
```

---

## 시나리오 5. 주문

### 5-1. 주문 생성

상품 존재 여부를 검증하고 PENDING_PAYMENT 상태의 주문을 생성한다. 재고는 이 단계에서 변경하지 않는다.

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: POST /api/v1/orders
    Controller->>+Facade: createOrder(userId, orderItems)

    alt 주문 항목 없음
        Facade-->>Controller: CoreException(BAD_REQUEST)
        Controller-->>Client: 400
    else 항목 있음
        loop 각 주문 항목
            Facade->>DB: SELECT product WHERE id = productId
            DB-->>Facade: product | null
            alt 상품 없음 또는 삭제됨
                Facade-->>Controller: CoreException(NOT_FOUND)
                Controller-->>Client: 404
            end
        end
        Note over Facade: OrderService.createOrder() → 스냅샷 생성 (순수 도메인 로직)
        Facade->>DB: INSERT order + orderItems
        Controller-->>Client: 200
    end
    deactivate Facade
    deactivate Controller
```

### 5-2. 결제 진입 (재고 선점)

결제 화면 진입 시 재고를 선점한다. 재고가 부족하면 결제 진입 자체가 실패한다.

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: POST /api/v1/orders/{orderId}/pay/start
    Controller->>+Facade: startPayment(userId, orderId)
    Facade->>DB: SELECT order WHERE id = orderId
    DB-->>Facade: order | null

    alt 주문 없음
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Client: 404
    else 주문 존재
        Facade->>DB: SELECT stocks WHERE productId IN (...)
        DB-->>Facade: stocks
        loop 각 주문 항목
            Note over Facade: StockModel.reserve(quantity)
            alt 재고 부족
                Facade-->>Controller: CoreException(BAD_REQUEST)
                Controller-->>Client: 400
            end
        end
        Facade->>DB: UPDATE stocks (reservedStock 증가)
        Controller-->>Client: 200
    end
    deactivate Facade
    deactivate Controller
```

### 5-3. 결제 완료 (재고 확정)

결제가 완료되면 선점한 재고를 확정 차감하고 주문을 CONFIRMED 상태로 전환한다.

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: POST /api/v1/orders/{orderId}/pay/confirm
    Controller->>+Facade: confirmPayment(userId, orderId)
    Facade->>DB: SELECT order WHERE id = orderId
    DB-->>Facade: order | null

    alt 주문 없음
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Client: 404
    else 주문 존재
        Facade->>DB: SELECT stocks WHERE productId IN (...)
        DB-->>Facade: stocks
        loop 각 주문 항목
            Note over Facade: StockModel.confirm(quantity)
        end
        Note over Facade: OrderModel.confirm() → CONFIRMED
        Facade->>DB: UPDATE stocks (totalStock, reservedStock 감소)
        Facade->>DB: UPDATE order (status = CONFIRMED)
        Controller-->>Client: 200
    end
    deactivate Facade
    deactivate Controller
```

---

## 시나리오 6. 주문 내역 조회

### 6-1. 주문 목록 조회

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: GET /api/v1/orders?startAt=2026-01-01&endAt=2026-01-31
    Controller->>+Facade: getOrders(userId, startAt, endAt)
    Facade->>DB: SELECT orders WHERE userId AND createdAt BETWEEN startAt AND endAt
    DB-->>Facade: order list
    deactivate Facade
    Controller-->>-Client: 200
```

### 6-2. 주문 상세 조회

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Facade
    participant DB

    Client->>+Controller: GET /api/v1/orders/{orderId}
    Controller->>+Facade: getOrder(userId, orderId)
    Facade->>DB: SELECT order WHERE id = orderId
    DB-->>Facade: order | null

    alt 주문 없음
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Client: 404
    else 주문 존재
        alt 타인의 주문 접근
            Facade-->>Controller: CoreException(FORBIDDEN)
            Controller-->>Client: 403
        else 본인의 주문
            Controller-->>Client: 200
        end
    end
    deactivate Facade
    deactivate Controller
```

---

## 시나리오 7. 브랜드 관리 (어드민)

### 7-1. 브랜드 등록

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant Facade
    participant DB

    Admin->>+Controller: POST /api/v1/admin/brands
    Controller->>+Facade: createBrand(name, description)

    alt 브랜드명 없음
        Facade-->>Controller: CoreException(BAD_REQUEST)
        Controller-->>Admin: 400
    else 유효한 입력
        Facade->>DB: INSERT brand
        Controller-->>Admin: 200
    end
    deactivate Facade
    deactivate Controller
```

### 7-2. 브랜드 수정

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant Facade
    participant DB

    Admin->>+Controller: PATCH /api/v1/admin/brands/{brandId}
    Controller->>+Facade: updateBrand(brandId, name, description)
    Facade->>DB: SELECT brand WHERE id = brandId
    DB-->>Facade: brand | null

    alt 브랜드 없음 또는 삭제됨
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Admin: 404
    else 브랜드 존재
        Facade->>DB: UPDATE brand
        Controller-->>Admin: 200
    end
    deactivate Facade
    deactivate Controller
```

### 7-3. 브랜드 삭제

소속 상품 삭제와 브랜드 삭제를 하나의 트랜잭션 안에서 처리한다.

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant Facade
    participant DB

    Admin->>+Controller: DELETE /api/v1/admin/brands/{brandId}
    Controller->>+Facade: deleteBrand(brandId)
    Facade->>DB: SELECT brand WHERE id = brandId
    DB-->>Facade: brand | null

    alt 브랜드 없음 또는 삭제됨
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Admin: 404
    else 브랜드 존재
        Facade->>DB: SELECT products WHERE brandId
        DB-->>Facade: products
        Note over Facade: BrandService.deleteCascade(brand, products)
        Note over Facade,DB: JPA dirty checking — products / brand UPDATE 자동 반영
        Controller-->>Admin: 200
    end
    deactivate Facade
    deactivate Controller
```

---

## 시나리오 8. 상품 관리 (어드민)

### 8-1. 상품 등록

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant Facade
    participant DB

    Admin->>+Controller: POST /api/v1/admin/products
    Controller->>+Facade: createProduct(name, description, price, stock, brandId)

    alt 필수 입력값 누락 또는 가격·재고 < 0
        Facade-->>Controller: CoreException(BAD_REQUEST)
        Controller-->>Admin: 400
    else 유효한 입력
        alt brandId가 제공된 경우
            Facade->>DB: SELECT brand WHERE id = brandId
            DB-->>Facade: brand | null
            alt 브랜드 없음 또는 삭제됨
                Facade-->>Controller: CoreException(NOT_FOUND)
                Controller-->>Admin: 404
            end
        end
        Facade->>DB: INSERT product
        Facade->>DB: INSERT stock (totalStock = initialStock)
        Controller-->>Admin: 200
    end
    deactivate Facade
    deactivate Controller
```

### 8-2. 상품 수정

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant Facade
    participant DB

    Admin->>+Controller: PATCH /api/v1/admin/products/{productId}
    Controller->>+Facade: updateProduct(productId, name, description, price)
    Facade->>DB: SELECT product WHERE id = productId
    DB-->>Facade: product | null

    alt 상품 없음 또는 삭제됨
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Admin: 404
    else 상품 존재
        Facade->>DB: UPDATE product
        Facade->>DB: SELECT stock WHERE productId (응답용)
        Controller-->>Admin: 200
    end
    deactivate Facade
    deactivate Controller
```

### 8-3. 상품 삭제

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant Facade
    participant DB

    Admin->>+Controller: DELETE /api/v1/admin/products/{productId}
    Controller->>+Facade: deleteProduct(productId)
    Facade->>DB: SELECT product WHERE id = productId
    DB-->>Facade: product | null

    alt 상품 없음 또는 삭제됨
        Facade-->>Controller: CoreException(NOT_FOUND)
        Controller-->>Admin: 404
    else 상품 존재
        Facade->>DB: UPDATE product (soft delete)
        Controller-->>Admin: 200
    end
    deactivate Facade
    deactivate Controller
```
