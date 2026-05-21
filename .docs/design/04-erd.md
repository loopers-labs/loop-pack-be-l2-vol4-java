# 04. ERD

> **스코프**: 영속화 스키마. 컨텍스트 경계는 03 클래스 다이어그램 참조.
> **표기 규칙**: 실선 `||--`은 FK 강제(같은 컨텍스트), 점선 `||..`은 FK 미강제 논리 참조(외부/스냅샷).

```mermaid
erDiagram
    %% Cross-context / 스냅샷 (FK 미강제 논리 참조)
    USERS ||..o{ ORDERS : places
    USERS ||..o{ PRODUCT_LIKE : likes
    PRODUCT ||..o{ PRODUCT_LIKE : likedBy
    PRODUCT ||..o{ ORDER_ITEM : snapshotRef

    %% Same-context / Aggregate-internal (FK 강제)
    BRAND ||--o{ PRODUCT : owns
    PRODUCT ||--|| STOCK : manages
    ORDERS ||--|{ ORDER_ITEM : contains

    USERS {
        BIGINT id PK
        VARCHAR login_id UK
        VARCHAR password
        VARCHAR name
        DATE birth_date
        VARCHAR email
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    BRAND {
        BIGINT id PK
        VARCHAR name
        VARCHAR description
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    PRODUCT {
        BIGINT id PK
        BIGINT brand_id FK
        VARCHAR name
        VARCHAR description
        BIGINT price
        BIGINT like_count
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    STOCK {
        BIGINT id PK
        BIGINT product_id UK
        INT quantity
        DATETIME created_at
        DATETIME updated_at
    }

    PRODUCT_LIKE {
        BIGINT id PK
        BIGINT user_id
        BIGINT product_id
        DATETIME created_at
    }

    ORDERS {
        BIGINT id PK
        BIGINT user_id
        VARCHAR status
        BIGINT total_amount
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    ORDER_ITEM {
        BIGINT id PK
        BIGINT order_id FK
        BIGINT product_id
        VARCHAR product_name
        BIGINT unit_price
        INT quantity
        DATETIME created_at
    }
```
- Stock은 동시성 제어가 필수이고 변경 패턴이 Product 본체와 크게 달라 별 엔티티로 분리. 단, 트랜잭션 일관성을 위해 Product Aggregate 내부에 둠 (@OneToOne 또는 별 테이블 + 같은 트랜잭션). like_count는 약한 일관성으로 충분해 Product 컬럼으로 유지.
