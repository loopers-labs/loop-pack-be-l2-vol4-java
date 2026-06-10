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

    INVENTORY {
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
        text snapshot "주문 전체 JSON 스냅샷 (ADR-028)"
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    COUPON_TEMPLATES {
        bigint id PK
        varchar name
        varchar type "FIXED | RATE"
        bigint value "정액: 원, 정률: %"
        bigint min_order_amount "nullable"
        datetime expired_at
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    COUPONS {
        bigint id PK
        bigint coupon_template_id
        bigint user_id
        varchar status "AVAILABLE | USED (EXPIRED는 lazy 계산, DB 미저장)"
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    BRAND ||--o{ PRODUCT : "has"
    PRODUCT ||--|| INVENTORY : "has"
    USERS ||--o{ LIKES : "likes"
    USERS ||--o{ ORDERS : "places"
    PRODUCT ||--o{ LIKES : "liked by"
    USERS ||--o{ COUPONS : "owns"
    COUPON_TEMPLATES ||--o{ COUPONS : "issued as"
```

## 설계 주의사항

| 컬럼 | 테이블 | 설명 |
|---|---|---|
| `brand_id` | PRODUCT | FK 컬럼 존재, DB 레벨 FK 제약조건 없음 (ADR-005) |
| `like_count` | PRODUCT | 좋아요 수 비정규화 컬럼, SQL 원자적 증감으로 관리 (ADR-003) |
| `product_id` | INVENTORY | 1:1 관계, ID만 저장, JPA 관계 없음 (ADR-006) |
| `user_id` | LIKES, ORDERS, COUPONS | ID만 저장, JPA 관계 없음 |
| `product_id` | LIKES | ID만 저장, JPA 관계 없음 |
| `snapshot` | ORDERS | 주문 전체 JSON 스냅샷 — items + 금액 + couponId (ADR-028). ORDER_ITEM 테이블 대체 |
| `UNIQUE(user_id, product_id)` | LIKES | 동일 유저의 중복 좋아요 방지 (DB 레벨 보장) |
| `UNIQUE(product_id)` | INVENTORY | 상품당 재고 행 1개 보장 |
| `status` | COUPONS | DB 값이 AVAILABLE이어도 expired_at 기준으로 lazy하게 EXPIRED 처리 (ADR-029) |
| `template_id` | COUPONS | FK 컬럼 존재, DB 레벨 FK 제약조건 없음 (ADR-005 원칙 준수) |
