```mermaid
erDiagram
  USERS {
    bigint id PK
    varchar login_id UK
    varchar login_password
    varchar name
    Date birthday
    varchar email
    timestamp created_at
    timestamp updated_at
  }

  BRAND {
    bigint id PK
    varchar name
    varchar description
    timestamp created_at
    timestamp updated_at
    timestamp deleted_at
  }

  PRODUCT {
    bigint id PK
    bigint brand_id FK
    varchar name
    decimal price
    int like_count
    timestamp created_at
    timestamp updated_at
    timestamp deleted_at
  }

  PRODUCT_STOCK {
    bigint product_id FK
    bigint quantity
    timestamp updated_at
  }

  PRODUCT_LIKE {
    bigint user_id FK
    bigint product_id FK
    timestamp created_at
  }

  ORDER {
    bigint id PK
    bigint user_id FK
    varchar status
    decimal total_price
    timestamp created_at
    timestamp updated_at
  }

  ORDER_ITEM {
    bigint id PK
    bigint order_id FK
    varchar product_id FK
    varchar product_name
    int product_price
    int quantity
  }

  OUTBOX_EVENT {
    bigint id PK
    varchar event_type
    varchar payload
    varchar status
    int retry_count
    timestamp created_at
    timestamp processed_at
  }

  BRAND ||--o{ PRODUCT : "1:N"
	PRODUCT ||--|| PRODUCT_STOCK : "1:1"
	USER ||--o{ PRODUCT_LIKE : "1:N"
	PRODUCT ||--o{ PRODUCT_LIKE : "1:N"
	USER ||--o{ ORDER : "1:N"
	ORDER ||--o{ ORDER_ITEM : "1:N"
	PRODUCT ||--o{ ORDER_ITEM : "1:N"
```

## 인덱스 제안

| 테이블 | 인덱스명 | 컬럼(들) | 타입 | 용도 |
|---|---|---|---|---|
| USERS | `uk_users_login_id` | `login_id` | UNIQUE | 로그인 조회 |
| PRODUCT | `idx_product_brand_id` | `brand_id` | INDEX | 브랜드별 상품 조회 |
| PRODUCT | `idx_product_price` | `price` | INDEX | 가격순 정렬 + 페이지네이션 |
| PRODUCT_LIKE | `uk_product_like_user_product` | `(user_id, product_id)` | UNIQUE | 중복 좋아요 방지, 유저별 좋아요 조회 |
| PRODUCT_LIKE | `idx_product_like_product_id` | `product_id` | INDEX | 상품별 좋아요 수 집계 |
| ORDER | `idx_order_user_id_created_at` | `(user_id, created_at)` | COMPOSITE | 사용자 주문 내역 최신순 조회 |
| ORDER | `idx_order_status` | `status` | INDEX | 주문 상태별 조회 |
| ORDER_ITEM | `idx_order_item_order_id` | `order_id` | INDEX | 주문 상세 조회 |
| OUTBOX_EVENT | `idx_outbox_status` | `status` | INDEX | 미처리 이벤트 폴링 |