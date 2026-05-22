# ERD (Entity Relationship Diagram)

---

```mermaid
erDiagram
    users {
        bigint id PK
        varchar login_id UK
        varchar encoded_password
        timestamp created_at
        timestamp deleted_at
    }

    brands {
        bigint id PK
        varchar name UK
        timestamp created_at
        timestamp deleted_at
    }

    products {
        bigint id PK
        bigint brand_id FK
        varchar name
        bigint price
        int stock
        int like_count
        varchar category_large
        varchar category_middle "nullable"
        varchar category_small "nullable"
        timestamp created_at
        timestamp deleted_at
    }

    likes {
        bigint user_id PK, FK
        bigint product_id PK, FK
        timestamp created_at
    }

    orders {
        bigint id PK
        bigint user_id FK
        varchar status "PENDING_PAYMENT | PAID | CANCELLED"
        timestamp ordered_at
    }

    order_items {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        int quantity
        varchar snapshot_product_name
        bigint snapshot_price
        varchar snapshot_brand_name
    }

    cart_items {
        bigint id PK
        bigint user_id FK
        bigint product_id FK
        int quantity
        timestamp added_at
    }

    payments {
        bigint id PK
        bigint order_id FK, UK
        bigint user_id FK
        bigint coupon_id FK "nullable"
        bigint amount
        bigint discount_amount
        varchar status "COMPLETED | CANCELLED"
        timestamp paid_at
        timestamp cancelled_at "nullable"
    }

    coupons {
        bigint id PK
        bigint user_id FK
        bigint discount_amount
        varchar status "AVAILABLE | USED"
        timestamp used_at "nullable"
    }

    brands    ||--o{ products   : ""
    users     ||--o{ likes      : ""
    products  ||--o{ likes      : ""
    users     ||--o{ orders     : ""
    orders    ||--|{ order_items : ""
    products  ||--o{ order_items : ""
    users     ||--o{ cart_items : ""
    products  ||--o{ cart_items : ""
    users     ||--o{ payments   : ""
    orders    ||--o| payments   : ""
    users     ||--o{ coupons    : ""
    coupons   o|--o| payments   : ""
```

---

## 설계 결정 사항

### Soft Delete 적용 대상

| 테이블 | 방식 | 이유 |
|--------|------|------|
| users | `deleted_at` | 탈퇴 유저의 주문·결제 이력 보존 필요 |
| brands | `deleted_at` | 삭제된 브랜드 상품도 함께 soft delete. order_items 스냅샷과 연결성 유지 |
| products | `deleted_at` | 삭제된 상품도 기존 주문 내역에서 조회 가능해야 함. 좋아요·장바구니 조회 시 `deleted_at IS NULL` 필터링 |
| orders | 미적용 | 주문은 취소돼도 이력 보존. status로 상태 관리 |
| payments | 미적용 | 결제 이력은 삭제 없음. status + cancelled_at으로 상태 관리 |
| coupons | 미적용 | status(AVAILABLE/USED)로 상태 관리 |
| likes | 미적용 | 취소 시 행 자체를 삭제 |
| cart_items | 미적용 | 제거 시 행 자체를 삭제 |
| order_items | 미적용 | 주문 이력이므로 삭제 없음 |

---

### Enum → VARCHAR

| 테이블 | 컬럼 | 값 |
|--------|------|----|
| orders | status | `PENDING_PAYMENT`, `PAID`, `CANCELLED` |
| payments | status | `COMPLETED`, `CANCELLED` |
| coupons | status | `AVAILABLE`, `USED` |

---

### Unique 제약

| 테이블 | 대상 | 이유 |
|--------|------|------|
| users | `login_id` | 중복 로그인 ID 불가 |
| brands | `name` | 브랜드명 중복 불가 |
| payments | `order_id` | 주문 1건에 결제 1건만 허용 |
| cart_items | `(user_id, product_id)` 복합 UK | 동일 상품 중복 담기 시 수량 증가로 처리. 행 중복 방지 |
| likes | `(user_id, product_id)` 복합 PK | 동일 상품 중복 좋아요 방지 |

---

### ProductSnapshot 저장 방식

`order_items`에 컬럼으로 직접 비정규화 저장. 주문 이후 상품 정보가 변경되거나 삭제돼도 주문 당시 데이터를 보존하기 위함.

| 컬럼 | 원본 출처 |
|------|-----------|
| `snapshot_product_name` | `products.name` |
| `snapshot_price` | `products.price` |
| `snapshot_brand_name` | `brands.name` |

---

### payments.coupon_id (nullable FK)

결제 시 쿠폰 사용은 선택. 쿠폰 없이 결제하면 `coupon_id = NULL`, `discount_amount = 0`.

---

### payments.cancelled_at (nullable)

`PaymentCancelInfo` VO를 단일 컬럼으로 표현. 취소 전에는 `NULL`, 취소 시 취소 일시가 기록됨. 전액 환불이므로 환불 금액은 별도 저장 없이 `amount`를 그대로 사용.
