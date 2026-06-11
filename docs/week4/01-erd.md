# 01. ERD (Week 4 — Coupon 도메인 추가)

> Week 2 ERD 기반에서 쿠폰 도메인과 주문 금액 스냅샷 컬럼을 추가합니다.

---

## 1. ERD 다이어그램

```mermaid
erDiagram
    users {
        BIGINT      id              PK  "AUTO_INCREMENT"
        VARCHAR_50  login_id        UK  "NOT NULL"
        VARCHAR_255 password            "NOT NULL"
        VARCHAR_100 name                "NOT NULL"
        DATE        birth_date          "NOT NULL"
        VARCHAR_255 email               "NOT NULL"
        DATETIME    created_at          "NOT NULL"
        DATETIME    updated_at          "NOT NULL"
        DATETIME    deleted_at          "NULL = 정상"
    }

    coupons {
        BIGINT      id              PK  "AUTO_INCREMENT"
        VARCHAR_100 name                "NOT NULL"
        ENUM        type                "NOT NULL, FIXED|RATE"
        INT         value               "NOT NULL, >0"
        INT         min_order_amount    "NULL 허용"
        DATETIME    expired_at          "NOT NULL"
        DATETIME    created_at          "NOT NULL"
        DATETIME    updated_at          "NOT NULL"
        DATETIME    deleted_at          "NULL = 정상, NOT NULL = 삭제"
    }

    user_coupons {
        BIGINT      id              PK  "AUTO_INCREMENT"
        BIGINT      user_id         FK  "NOT NULL"
        BIGINT      coupon_id       FK  "NOT NULL"
        ENUM        status              "NOT NULL, AVAILABLE|USED"
        BIGINT      version             "NOT NULL, 낙관적 락"
        DATETIME    created_at          "NOT NULL"
        DATETIME    updated_at          "NOT NULL"
        DATETIME    deleted_at          "NULL = 정상"
    }

    orders {
        BIGINT      id              PK  "AUTO_INCREMENT"
        BIGINT      user_id         FK  "NOT NULL"
        ENUM        status              "NOT NULL, PENDING|CONFIRMED|CANCELLED"
        INT         original_amount     "NOT NULL, 쿠폰 적용 전 금액"
        INT         discount_amount     "NOT NULL, 할인 금액 (미적용 시 0)"
        INT         total_amount        "NOT NULL, 최종 결제 금액"
        DATETIME    created_at          "NOT NULL"
        DATETIME    updated_at          "NOT NULL"
        DATETIME    deleted_at          "NULL = 정상"
    }

    order_items {
        BIGINT      id              PK  "AUTO_INCREMENT"
        BIGINT      order_id        FK  "NOT NULL"
        BIGINT      product_id          "NOT NULL"
        VARCHAR_200 product_name        "NOT NULL"
        INT         unit_price          "NOT NULL"
        VARCHAR_100 brand_name          "NOT NULL"
        INT         quantity            "NOT NULL, >=1"
        DATETIME    created_at          "NOT NULL"
        DATETIME    updated_at          "NOT NULL"
        DATETIME    deleted_at          "NULL = 정상"
    }

    brands {
        BIGINT      id              PK  "AUTO_INCREMENT"
        VARCHAR_100 name            UK  "NOT NULL"
        TEXT        description         "NULL 허용"
        DATETIME    created_at          "NOT NULL"
        DATETIME    updated_at          "NOT NULL"
        DATETIME    deleted_at          "NULL = 정상"
    }

    products {
        BIGINT      id              PK  "AUTO_INCREMENT"
        BIGINT      brand_id        FK  "NOT NULL"
        VARCHAR_200 name                "NOT NULL"
        INT         price               "NOT NULL, >0"
        DATETIME    created_at          "NOT NULL"
        DATETIME    updated_at          "NOT NULL"
        DATETIME    deleted_at          "NULL = 정상"
    }

    stocks {
        BIGINT      id              PK  "AUTO_INCREMENT"
        BIGINT      product_id      FK  "NOT NULL, UNIQUE"
        INT         quantity            "NOT NULL, >=0"
        BIGINT      version             "NOT NULL, 낙관적 락 (week5 이후)"
        DATETIME    created_at          "NOT NULL"
        DATETIME    updated_at          "NOT NULL"
        DATETIME    deleted_at          "NULL = 정상"
    }

    users        ||--o{ orders       : "1명은 여러 주문을 가진다"
    users        ||--o{ user_coupons : "1명은 여러 발급 쿠폰을 가진다"
    coupons      ||--o{ user_coupons : "1개 쿠폰은 여러 사용자에게 발급된다"
    orders       ||--|{ order_items  : "1개 주문은 1개 이상의 항목을 가진다"
    brands       ||--o{ products     : "1개 브랜드는 여러 상품을 가진다"
    products     ||--|| stocks       : "1개 상품은 1개의 재고를 가진다"
```

---

## 2. 신규 / 변경 테이블 상세

### 2-1. `coupons` — 쿠폰 템플릿 (신규)

| 컬럼명 | 타입 | NULL | 제약 | 설명 |
|--------|------|------|------|------|
| `id` | BIGINT | NOT NULL | PK | 쿠폰 템플릿 식별자 |
| `name` | VARCHAR(100) | NOT NULL | — | 쿠폰명 |
| `type` | ENUM | NOT NULL | FIXED \| RATE | 정액 / 정률 |
| `value` | INT | NOT NULL | CHECK (> 0) | FIXED: 할인 금액(원), RATE: 할인율(%) |
| `min_order_amount` | INT | NULL | — | 최소 주문 금액 조건 |
| `expired_at` | DATETIME | NOT NULL | — | 쿠폰 만료 일시 |
| `deleted_at` | DATETIME | NULL | — | 소프트 딜리트 |

### 2-2. `user_coupons` — 발급된 쿠폰 (신규)

| 컬럼명 | 타입 | NULL | 제약 | 설명 |
|--------|------|------|------|------|
| `id` | BIGINT | NOT NULL | PK | 발급 쿠폰 식별자 |
| `user_id` | BIGINT | NOT NULL | FK → users(id) | 소유 사용자 |
| `coupon_id` | BIGINT | NOT NULL | FK → coupons(id) | 원본 쿠폰 템플릿 |
| `status` | ENUM | NOT NULL | AVAILABLE \| USED | 사용 상태 (EXPIRED는 만료일 기반 동적 계산) |
| `version` | BIGINT | NOT NULL | — | 낙관적 락용 버전 필드 |

**제약**
- `UNIQUE (user_id, coupon_id)` — 1인 1회 발급 보장
- EXPIRED 상태는 DB에 저장하지 않으며, `coupons.expired_at` 기준으로 응답 시 동적 계산

### 2-3. `orders` — 주문 (변경)

| 컬럼명 | 변경 내용 | 설명 |
|--------|----------|------|
| `original_amount` | **신규** | 쿠폰 적용 전 원래 주문 금액 |
| `discount_amount` | **신규** | 할인 금액 (쿠폰 미적용 시 0) |
| `total_amount` | 의미 변경 | 최종 결제 금액 = original_amount − discount_amount |

---

## 3. 설계 결정

| 결정 | 내용 | 근거 |
|------|------|------|
| **EXPIRED 동적 계산** | `user_coupons.status`는 AVAILABLE/USED만 저장 | 배치 없이도 만료 여부를 실시간 반영. `expired_at` 변경 시 즉시 적용 가능 |
| **낙관적 락** | `user_coupons.version` 필드 | 쿠폰은 소유자 1인이 여러 기기에서 동시 사용하는 낮은 충돌 확률 → 비관적 락 없이 충분 |
| **1인 1회 발급** | `UNIQUE (user_id, coupon_id)` | 중복 발급을 DB 레벨에서 차단 |
| **주문 금액 3분할** | `original_amount`, `discount_amount`, `total_amount` | 주문은 계약이며, 할인 내역도 스냅샷으로 보존 |
