# ERD

## 전체 ERD

영속성 구조, 테이블 간 관계, soft delete 컬럼 위치, 스냅샷 컬럼 위치, UK 제약을 확인한다.

```mermaid
erDiagram
    users {
        bigint id PK
        varchar login_id UK
        varchar password
        varchar name
        date birth_date
        varchar email
        datetime created_at
        datetime updated_at
    }

    brands {
        bigint id PK
        varchar name
        varchar description
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    products {
        bigint id PK
        bigint brand_id FK
        varchar name
        int price
        int stock
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    likes {
        bigint id PK
        bigint user_id FK
        bigint product_id FK
        datetime created_at
    }

    orders {
        bigint id PK
        bigint user_id FK
        varchar status
        int total_price
        datetime ordered_at
        datetime created_at
        datetime updated_at
    }

    order_items {
        bigint id PK
        bigint order_id FK
        bigint product_id
        varchar product_name
        int product_price
        int quantity
    }

    users ||--o{ likes : ""
    users ||--o{ orders : ""
    brands ||--o{ products : ""
    products ||--o{ likes : ""
    orders ||--o{ order_items : ""
```

**읽는 포인트**
- `brands`, `products` 모두 `deleted_at` 컬럼 보유. 브랜드 삭제 시 연관 상품의 `deleted_at`도 함께 채운다. 조회 시 `deleted_at IS NULL` 조건 필수.
- `order_items.product_id`는 FK 제약 없음. 상품이 삭제되어도 주문 내역은 `product_name`, `product_price` 스냅샷으로 독립 보존된다.
- `likes`는 `(user_id, product_id)` 복합 UK 제약으로 DB 레벨에서 중복 좋아요 방지.

---

## 테이블별 설계 설명

### brands
- `deleted_at`: soft delete 컬럼. null이면 활성, 값이 있으면 삭제 처리.

### products
- `stock`: 주문 시 차감되는 재고 수량. 어드민에게만 수량 노출, 사용자에게는 `stock > 0` 여부만 노출.
- `brand_id`: 브랜드 FK. 상품 수정 시 변경 불가.
- `deleted_at`: 브랜드 삭제 시 연관 상품에도 함께 채워진다.

### likes
- `(user_id, product_id)` 복합 UK 제약으로 중복 좋아요 방지.
- 좋아요 수는 별도 컬럼 없이 COUNT 집계로 처리. 성능 이슈 발생 시 `products.like_count` 캐시 컬럼 도입 검토.

### orders
- `status`: `PENDING` → `COMPLETED` / `CANCELLED` 상태 전이.
- `total_price`: 주문 시점 총 금액. 이후 상품 가격 변동과 무관하게 보존.
- `ordered_at`: 주문 요청 시각.

### order_items
- `product_name`, `product_price`: 주문 당시 상품 정보 스냅샷. `product_id`가 가리키는 상품이 변경/삭제되어도 주문 내역은 영향받지 않는다.
- `product_id`: FK 제약 없이 참조용으로만 보유.

---

## 설계 고민

**좋아요 수 정렬 (`likes_desc`)**
- 현재: `likes` 테이블 COUNT JOIN으로 집계 → 항상 정확하지만 상품 수가 많아지면 느려질 수 있음
- 추후: `products.like_count` 캐시 컬럼 도입 → 빠르지만 좋아요 등록/취소 시 동기화 필요, 동시성 문제 고려 필요

**`order_items.product_id` FK 제약**
- FK 제약을 걸면 상품 물리 삭제 시 주문 내역도 영향받음
- soft delete 방식을 쓰더라도 FK 제약 없이 참조용으로만 두는 것이 안전
