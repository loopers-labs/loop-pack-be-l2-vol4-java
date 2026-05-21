# ERD

---

```mermaid
erDiagram
    BRAND {
        bigint id PK
        varchar name
        varchar description
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    PRODUCT {
        bigint id PK
        bigint brand_id
        varchar name
        text description
        bigint price
        bigint like_count
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    PRODUCT_INVENTORY {
        bigint id PK
        bigint product_id UK
        int quantity
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    USERS {
        bigint id PK
        varchar user_id UK
        varchar name
        varchar email
        varchar password
        date birth_date
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    LIKES {
        bigint id PK
        bigint user_id "UK(user_id, product_id)"
        bigint product_id "UK(user_id, product_id)"
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    ORDERS {
        bigint id PK
        bigint user_id
        varchar status
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    ORDER_ITEM {
        bigint id PK
        bigint order_id
        bigint product_id
        varchar product_name
        bigint product_price
        int quantity
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    BRAND ||--o{ PRODUCT : "has"
    PRODUCT ||--|| PRODUCT_INVENTORY : "has"
    USERS ||--o{ LIKES : "likes"
    USERS ||--o{ ORDERS : "places"
    PRODUCT ||--o{ LIKES : "liked by"
    PRODUCT ||--o{ ORDER_ITEM : "referenced by"
    ORDERS ||--|{ ORDER_ITEM : "contains"
```

## 설계 주의사항

| 컬럼 | 테이블 | 설명 |
|---|---|---|
| `brand_id` | PRODUCT | FK 컬럼 존재, DB 레벨 FK 제약조건 없음 (ADR-005) |
| `like_count` | PRODUCT | 좋아요 수 비정규화 컬럼, SQL 원자적 증감으로 관리 (ADR-003) |
| `product_id` | PRODUCT_INVENTORY | 1:1 관계, ID만 저장, JPA 관계 없음 (ADR-006) |
| `user_id` | LIKES, ORDERS | ID만 저장, JPA 관계 없음 |
| `product_id` | LIKES, ORDER_ITEM | ID만 저장, JPA 관계 없음 |
| `product_name`, `product_price` | ORDER_ITEM | 주문 시점 스냅샷 (ADR-001) |
| `UNIQUE(user_id, product_id)` | LIKES | 동일 유저의 중복 좋아요 방지 (DB 레벨 보장) |
| `UNIQUE(product_id)` | PRODUCT_INVENTORY | 상품당 재고 행 1개 보장 |
