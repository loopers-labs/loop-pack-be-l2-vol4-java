# 04. ERD

```mermaid
erDiagram
    base_entity {
        datetime created_at
        datetime updated_at
        boolean is_deleted
    }

    member {
        bigint id PK
        varchar login_id UK
        varchar password_hash
        varchar status
    }

    brand {
        bigint id PK
        varchar name
        varchar description
        varchar status
    }

    product {
        bigint id PK
        bigint brand_id FK
        varchar name
        varchar description
        decimal price_amount
        varchar price_currency
        varchar status
    }

    inventory {
        bigint id PK
        bigint product_id FK, UK
        int available_quantity
        varchar status
        bigint version
    }

    product_like {
        bigint id PK
        bigint member_id FK
        bigint product_id FK
        varchar status
        datetime liked_at
        datetime canceled_at
    }

    coupon {
        bigint id PK
        varchar name
        varchar discount_type
        decimal discount_value
        decimal min_order_amount
        boolean duplicate_issue_allowed
        datetime issue_start_at
        datetime issue_end_at
        datetime expires_at
        varchar status
    }

    member_coupon {
        bigint id PK
        bigint member_id FK
        bigint coupon_id FK
        bigint order_id FK
        varchar status
        datetime issued_at
        datetime used_at
        datetime restored_at
        datetime expired_at
    }

    order {
        bigint id PK
        bigint member_id FK
        varchar status
        decimal total_amount
        decimal discount_amount
        decimal payment_amount
        varchar currency
        datetime ordered_at
        datetime paid_at
        datetime payment_failed_at
        datetime canceled_at
    }

    order_item {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        bigint brand_id
        varchar snapshot_product_name
        varchar snapshot_brand_name
        decimal snapshot_unit_price_amount
        varchar snapshot_unit_price_currency
        int quantity
        decimal line_amount
    }

    payment {
        bigint id PK
        bigint order_id FK, UK
        decimal amount
        varchar currency
        varchar status
        varchar failure_reason
        varchar external_transaction_key
        datetime requested_at
        datetime approved_at
        datetime failed_at
        datetime canceled_at
    }

    user_action_log {
        bigint id PK
        bigint member_id FK
        varchar action_type
        varchar target_type
        bigint target_id
        text metadata
        datetime recorded_at
    }

    outbox_event {
        bigint id PK
        varchar event_type
        varchar aggregate_type
        bigint aggregate_id
        text payload
        varchar publish_status
        int retry_count
        datetime occurred_at
        datetime published_at
    }

    base_entity ||--|| member : extends
    base_entity ||--|| brand : extends
    base_entity ||--|| product : extends
    base_entity ||--|| inventory : extends
    base_entity ||--|| product_like : extends
    base_entity ||--|| coupon : extends
    base_entity ||--|| member_coupon : extends
    base_entity ||--|| order : extends
    base_entity ||--|| order_item : extends
    base_entity ||--|| payment : extends
    base_entity ||--|| user_action_log : extends
    base_entity ||--|| outbox_event : extends

    brand ||--o{ product : "product.brand_id"
    product ||--|| inventory : "inventory.product_id"

    member ||--o{ product_like : "product_like.member_id"
    product ||--o{ product_like : "product_like.product_id"

    member ||--o{ member_coupon : "member_coupon.member_id"
    coupon ||--o{ member_coupon : "member_coupon.coupon_id"
    order |o--o| member_coupon : "member_coupon.order_id"

    member ||--o{ order : "order.member_id"
    order ||--|{ order_item : "order_item.order_id"
    product ||--o{ order_item : "order_item.product_id"
    order ||--o| payment : "payment.order_id"

    member |o--o{ user_action_log : "user_action_log.member_id"
```
