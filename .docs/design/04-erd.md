# 04. ERD — 전체 테이블 구조 및 관계 정리

---

```mermaid
erDiagram
    users {
        BIGINT id PK
        VARCHAR login_id UK
        VARCHAR password
        VARCHAR email UK
        VARCHAR gender
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    brands {
        BIGINT id PK
        VARCHAR name UK
        VARCHAR description
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    products {
        BIGINT id PK
        BIGINT brand_id FK
        VARCHAR name
        BIGINT price
        INT stock
        VARCHAR description
        BIGINT like_count
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    likes {
        BIGINT id PK
        BIGINT user_id FK
        BIGINT product_id FK
        DATETIME created_at
        DATETIME updated_at
    }

    orders {
        BIGINT id PK
        BIGINT user_id FK
        DATETIME created_at
        DATETIME updated_at
    }

    order_items {
        BIGINT id PK
        BIGINT order_id FK
        BIGINT product_id
        VARCHAR product_name
        BIGINT price
        INT quantity
        DATETIME created_at
        DATETIME updated_at
    }

    users ||--o{ likes : ""
    users ||--o{ orders : ""
    brands ||--o{ products : ""
    products ||--o{ likes : ""
    orders ||--|{ order_items : ""
```

---

## 설계 결정 사항

### BaseEntity / SoftDeletableEntity 분리
`BaseEntity`는 `id`, `created_at`, `updated_at`만 담당한다. 소프트 딜리트가 필요한 엔티티만 `SoftDeletableEntity`(extends BaseEntity)를 상속해 `deleted_at`, `delete()`, `restore()`를 갖는다.

| 상속 | 테이블 |
|------|--------|
| SoftDeletableEntity | users, brands, products |
| BaseEntity | likes, orders, order_items |

`orders`와 `order_items`는 삭제 시나리오가 없고, 추후 주문 취소가 생기더라도 `deleted_at`이 아닌 `status` 필드로 상태를 표현하는 게 적합하다.

### likes — deleted_at 없음
하드 딜리트로 결정했으므로 `deleted_at` 컬럼을 두지 않는다. `BaseEntity`의 `id`, `created_at`, `updated_at`만 상속한다.

### order_items — product_id 논리 참조
`product_id`는 스냅샷 참조용으로만 보관한다. 주문 이후 상품이 삭제되어도 주문 기록이 유지되어야 하므로 FK 제약을 걸지 않는다.

### products — like_count
초기값 0으로 생성되며 좋아요 등록·취소 시 `like_count = like_count ± 1` 형태의 DB 원자 UPDATE로 갱신된다.

---

## 제약 조건

| 테이블 | 종류 | 대상 컬럼 |
|--------|------|-----------|
| users | UK | login_id |
| users | UK | email |
| brands | UK | name |
| likes | UK | (user_id, product_id) |
| products | FK | brand_id → brands.id |
| likes | FK | user_id → users.id |
| likes | FK | product_id → products.id |
| orders | FK | user_id → users.id |
| order_items | FK | order_id → orders.id |

---

## 인덱스 전략

| 테이블 | 인덱스명 | 컬럼 | 목적 |
|--------|----------|------|------|
| products | idx_products_brand_id | brand_id | 브랜드 필터 |
| products | idx_products_like_count | like_count DESC | likes_desc 정렬 |
| products | idx_products_price | price ASC | price_asc 정렬 |
| likes | uk_likes_user_product | (user_id, product_id) | 중복 체크 (UK가 인덱스 겸용) |
| likes | idx_likes_user_id | user_id | 내 좋아요 목록 조회 |
| orders | idx_orders_user_created | (user_id, created_at) | 날짜 범위 주문 목록 조회 |
| order_items | idx_order_items_order_id | order_id | 주문 항목 조회 (FK 인덱스 겸용) |
