# ERD

## 목적
영속성 구조에서 관계의 주인, 유니크 제약, 정규화 여부를 검증한다.

## 관계 표기 범례 (Crow's Foot)

| 표기 | 의미 | 이 ERD에서 사용 위치 |
|---|---|---|
| `\|\|--\|\|` | 1 : 1 (양쪽 반드시 1개) | product_options → stocks |
| `\|\|--o\|` | 1 : 0 or 1 (오른쪽 없을 수도 있음) | orders → payments |
| `\|\|--o{` | 1 : 0 or N (오른쪽 없거나 여러 개) | users → orders, products → likes 등 |
| `\|\|--\|{` | 1 : 1 or N (오른쪽 반드시 1개 이상) | _(현재 미사용)_ |

```
기호 단위 의미
  ||  →  정확히 1개
  o|  →  0 또는 1개
  o{  →  0개 이상 (없을 수도)
  |{  →  1개 이상 (반드시 존재)
```


## 다이어그램

```mermaid
erDiagram
    users["회원 (users)"] {
        bigint id PK "회원 고유 ID"
        varchar login_id UK "로그인 아이디 (영문/숫자 1~10자)"
        varchar password "암호화된 비밀번호 (BCrypt)"
        varchar name "회원 이름"
        varchar email "이메일 주소"
        varchar birth "생년월일 (YYYY-MM-DD)"
        bigint point "포인트 잔액 (atomic UPDATE로 동시성 보장)"
        datetime created_at "가입일시"
    }

    brands["브랜드 (brands)"] {
        bigint id PK "브랜드 고유 ID"
        varchar name "브랜드명"
        varchar description "브랜드 설명"
        datetime created_at "등록일시"
    }

    products["상품 (products)"] {
        bigint id PK "상품 고유 ID"
        bigint brand_id FK "브랜드 ID"
        varchar name "상품명"
        bigint price "기본 판매가 (원)"
        text description "상품 설명"
        bigint like_count "좋아요 수 (카운터 캐시, 비정규화)"
        datetime created_at "등록일시"
    }

    product_options["상품 옵션 (product_options)"] {
        bigint id PK "옵션 고유 ID"
        bigint product_id FK "상품 ID"
        varchar option_name "옵션명 (예: 기본 / 흰색-S / 검정-M)"
        bigint additional_price "옵션 추가금액 (기본 0원)"
        datetime created_at "등록일시"
    }

    stocks["재고 (stocks)"] {
        bigint id PK "재고 고유 ID"
        bigint product_option_id FK "상품 옵션 ID (UK, 옵션당 1개)"
        int total_quantity "실제 보유 수량 (결제 확정 시 차감)"
        int reserved_quantity "예약 중 수량 (주문 생성 시 증가)"
        datetime updated_at "최종 수정일시"
    }

    likes["좋아요 (likes)"] {
        bigint id PK "좋아요 고유 ID"
        bigint user_id FK "회원 ID"
        bigint product_id FK "상품 ID"
        datetime created_at "좋아요 등록일시"
    }

    orders["주문 (orders)"] {
        bigint id PK "주문 고유 ID"
        bigint user_id FK "회원 ID"
        varchar status "주문 상태 (PENDING/CONFIRMED/FAILED/CANCELLED)"
        bigint total_amount "총 주문금액 (point_amount + pg_amount)"
        bigint point_amount "포인트로 결제한 금액"
        bigint pg_amount "PG로 결제한 금액"
        varchar receiver_name "수령인 이름 (주문 시점 스냅샷)"
        varchar receiver_phone "수령인 연락처 (주문 시점 스냅샷)"
        varchar zip_code "우편번호 (주문 시점 스냅샷)"
        varchar address "기본 주소 (주문 시점 스냅샷)"
        varchar detail_address "상세 주소 (주문 시점 스냅샷)"
        datetime created_at "주문일시"
    }

    order_items["주문 상품 (order_items)"] {
        bigint id PK "주문 상품 고유 ID"
        bigint order_id FK "주문 ID"
        bigint product_id FK "상품 ID"
        bigint product_option_id FK "상품 옵션 ID"
        varchar product_name "상품명 (주문 시점 스냅샷)"
        varchar option_name "옵션명 (주문 시점 스냅샷)"
        int quantity "주문 수량"
        bigint price "단가 (price + additional_price, 주문 시점 스냅샷)"
    }

    payments["결제 (payments)"] {
        bigint id PK "결제 고유 ID"
        bigint order_id FK "주문 ID (UK, 주문당 1건)"
        varchar pg_transaction_id UK "PG 트랜잭션 ID (중복 방지)"
        varchar status "결제 상태 (SUCCESS/FAILED)"
        bigint amount "결제 금액"
        datetime created_at "결제일시"
    }

    point_histories["포인트 이력 (point_histories)"] {
        bigint id PK "이력 고유 ID"
        bigint user_id FK "회원 ID"
        bigint order_id FK "주문 ID"
        varchar type "변경 유형 (EARN/USE/REFUND)"
        bigint amount "변경 금액 (양수: 적립/환불, 음수: 사용)"
        datetime created_at "변경일시"
    }

    users ||--o{ orders : "회원은 주문을 여러 개 할 수 있다"
    users ||--o{ likes : "회원은 좋아요를 여러 개 누를 수 있다"
    users ||--o{ point_histories : "회원의 포인트 변경 이력"
    brands ||--o{ products : "브랜드는 상품을 여러 개 가진다"
    products ||--o{ product_options : "상품은 옵션을 1개 이상 가진다"
    product_options ||--|| stocks : "옵션당 재고는 반드시 1개"
    products ||--o{ likes : "상품은 좋아요를 여러 개 받을 수 있다"
    products ||--o{ order_items : "상품은 여러 주문에 담길 수 있다"
    product_options ||--o{ order_items : "옵션 단위로 주문에 담긴다"
    orders ||--o{ order_items : "주문은 상품을 1개 이상 포함한다"
    orders ||--o| payments : "주문당 결제는 최대 1건"
    orders ||--o{ point_histories : "주문으로 인한 포인트 변경 이력"
```

## 테이블 설계 상세

### users
- `point`: 포인트 잔액. `UPDATE ... WHERE point >= ?` atomic UPDATE로 동시성 보장
- `login_id`: 유니크 제약. 중복 가입 방지

### brands
- 상품과 독립된 엔티티. 브랜드 단독 조회 가능
- 상품이 brand_id를 외래키로 참조

### products
- `brand_id`: brands 테이블 외래키
- `like_count`: 좋아요 수 카운터 캐시. 빠른 조회를 위해 비정규화
  - 좋아요 등록 시 likes INSERT + like_count +1 (같은 트랜잭션)
  - 좋아요 취소 시 likes DELETE + like_count -1 (같은 트랜잭션)
  - 정합성 보정: 주기적 배치로 `COUNT(*) FROM likes` 와 동기화
- 가격은 주문 시점 스냅샷이 order_items에 저장되므로 변경되어도 과거 주문에 영향 없음

### product_options
- `option_name`: 옵션명 (ex. "기본", "흰색/S", "검정/M")
- `additional_price`: 옵션 추가금액 (기본 0원). 실제 가격 = `products.price + additional_price`
- 옵션 없는 상품은 `option_name = "기본"`, `additional_price = 0` 인 옵션 1개 자동 생성

### stocks
- `product_option_id`: UK — 옵션당 재고 1개 (product_id → product_option_id로 변경)
- `total_quantity`: 실제 보유 재고 (결제 확정 시 차감)
- `reserved_quantity`: 예약 중인 수량 (주문 생성 시 증가)
- 가용 재고 = `total_quantity - reserved_quantity`
- `updated_at`: 재고 변경 추적용

### likes
- `(user_id, product_id)` 복합 유니크 제약 필요 → 중복 좋아요 DB 레벨 방어
- 멱등 토글: 존재하면 DELETE, 없으면 INSERT

### orders
- `status`: PENDING / CONFIRMED / FAILED / CANCELLED
- `total_amount`: 총 결제 금액 (point_amount + pg_amount)
- `point_amount`: 포인트로 결제한 금액
- `pg_amount`: PG로 결제한 금액
- 배송지 컬럼 (`receiver_name`, `receiver_phone`, `zip_code`, `address`, `detail_address`): 주문 시점 스냅샷
- `expires_at` 없음 (재시도 없음, 스케줄러가 created_at 기준으로 만료 판단)
- 인덱스: `(status, created_at)` → 스케줄러의 만료 PENDING 주문 조회 성능 보장

### order_items
- 주문 생성 시점에 INSERT되는 라인 아이템 테이블
- `product_name`: 당시 상품명 스냅샷
- `option_name`: 당시 옵션명 스냅샷 (ex. "기본", "흰색/S")
- `price`: 당시 단가 스냅샷 (`products.price + additional_price` 계산값)

### payments
- `order_id`: UK — 주문당 결제 1건 (재시도 없음)
- `pg_transaction_id`: UK — PG 트랜잭션 ID 중복 방지
- `status`: SUCCESS / FAILED

### point_histories
- append-only 이력 테이블
- `type`: EARN (적립) / USE (사용) / REFUND (환불)
- `amount`: 양수(적립/환불) / 음수(사용)
- `order_id`: 주문으로 인한 포인트 변경 추적

## 제약 조건 요약

| 테이블 | 제약 | 목적 |
|---|---|---|
| users.login_id | UNIQUE | 중복 가입 방지 |
| stocks.product_option_id | UNIQUE | 옵션당 재고 1개 보장 |
| likes.(user_id, product_id) | 복합 UNIQUE | 중복 좋아요 방지 |
| payments.order_id | UNIQUE | 주문당 결제 1건 |
| payments.pg_transaction_id | UNIQUE | PG 트랜잭션 중복 방지 |

## 인덱스 요약

| 테이블 | 인덱스 | 목적 |
|---|---|---|
| orders | `(status, created_at)` | 스케줄러의 만료 PENDING 주문 조회 풀스캔 방지 |
