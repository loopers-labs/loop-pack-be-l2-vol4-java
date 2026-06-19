# 05. 성능 벤치마크 결과

## 테스트 환경

| 항목 | 값 |
|------|-----|
| DB | MySQL 9.7 |
| products | 996,327건 |
| likes | 2,490,470건 |
| brands | 7,500개 (brand당 평균 133건) |
| like_count 분포 | 0개(164K), 1~10개(834K), 11~100개(82건), 100초과(1K건) |
| LIMIT | 20 |
| OFFSET 비교 | 0 (1페이지) vs 500,000 (25,000페이지) |

---

## 전체 벤치마크 결과

### [1] 인덱스 없음 — 브랜드별 좋아요 순

| 번호 | 구조 | OFFSET | type | rows | Extra | 실행시간 |
|------|------|--------|------|------|-------|---------|
| 1-1 | 정규화 (JOIN+COUNT) | 0 | ALL | 996,327 | Using temporary; Using filesort | 93.74ms |
| 1-2 | 정규화 | 500,000 | ALL | 996,327 | Using temporary; Using filesort | 94.71ms |
| 1-3 | 반정규화 | 0 | ALL | 996,327 | Using filesort | 103.38ms |
| 1-4 | 반정규화 | 500,000 | ALL | 996,327 | Using filesort | 101.61ms |

### [2] 단일 인덱스 `(brand_id)`

| 번호 | 구조 | OFFSET | type | key | rows | Extra | 실행시간 |
|------|------|--------|------|-----|------|-------|---------|
| 2-1 | 정규화 | 0 | ref | idx_products_brand_id | 1 | Using where; Using temporary; Using filesort | 0.11ms |
| 2-2 | 정규화 | 500,000 | ref | idx_products_brand_id | 1 | Using where; Using temporary; Using filesort | 0.08ms |
| 2-3 | 반정규화 | 0 | ref | idx_products_brand_id | 1 | Using where; Using filesort | 0.10ms |
| 2-4 | 반정규화 | 500,000 | ref | idx_products_brand_id | 1 | Using where; Using filesort | 0.09ms |

### [3] 복합 인덱스 `(brand_id, like_count DESC)`

| 번호 | 구조 | OFFSET | type | key | rows | Extra | 실행시간 |
|------|------|--------|------|-----|------|-------|---------|
| 3-1 | 정규화 | 0 | ref | idx_products_brand_like | 1 | Using where; Using temporary; Using filesort | 0.12ms |
| 3-2 | 정규화 | 500,000 | ref | idx_products_brand_like | 1 | Using where; Using temporary; Using filesort | 0.10ms |
| 3-3 | 반정규화 | 0 | ref | idx_products_brand_like | 1 | Using where | **0.09ms** |
| 3-4 | 반정규화 | 500,000 | ref | idx_products_brand_like | 1 | Using where | **0.08ms** |

### [4] 커버링 인덱스 `(brand_id, like_count DESC, id, name, price)`

| 번호 | 구조 | OFFSET | type | key | rows | Extra | 실행시간 |
|------|------|--------|------|-----|------|-------|---------|
| 4-1 | 정규화 | 0 | ref | idx_products_covering | 1 | Using where; Using temporary; Using filesort | 0.14ms |
| 4-2 | 정규화 | 500,000 | ref | idx_products_covering | 1 | Using where; Using temporary; Using filesort | 0.11ms |
| 4-3 | 반정규화 | 0 | ref | idx_products_covering | 1 | Using where | 0.12ms |
| 4-4 | 반정규화 | 500,000 | ref | idx_products_covering | 1 | Using where | 0.11ms |

### [5] 분리 테이블 `product_like_view` JOIN

| 번호 | 인덱스 | OFFSET | type(p) | key | rows | Extra | 실행시간 |
|------|--------|--------|---------|-----|------|-------|---------|
| 5-1 | 없음 | 0 | ALL | NULL | 996,327 | Using temporary; Using filesort | 110.92ms |
| 5-2 | 없음 | 500,000 | ALL | NULL | 996,327 | Using temporary; Using filesort | 110.30ms |
| 5-3 | 단일(brand_id) | 0 | ref | idx_products_brand_id | 1 | Using where; Using temporary; Using filesort | 0.12ms |
| 5-4 | 단일(brand_id) | 500,000 | ref | idx_products_brand_id | 1 | Using where; Using temporary; Using filesort | 0.08ms |
| 5-5 | 복합(brand+like) | 0 | ref | idx_products_brand_like | 1 | Using where; Using temporary; Using filesort | 0.12ms |
| 5-6 | 복합(brand+like) | 500,000 | ref | idx_products_brand_like | 1 | Using where; Using temporary; Using filesort | 0.09ms |
| 5-7 | 커버링 | 0 | ref | idx_products_covering | 1 | Using where; Using temporary; Using filesort | 0.15ms |
| 5-8 | 커버링 | 500,000 | ref | idx_products_covering | 1 | Using where; Using temporary; Using filesort | 0.12ms |

> 분리 테이블은 JOIN 때문에 인덱스 종류와 관계없이 항상 Using filesort 발생.
> 단, brand_id 필터 후 ~133건만 정렬하므로 실제 성능 차이는 0.04ms 이하.

### [6] 다양한 정렬 (brand_id=1)

| 번호 | 정렬 | 인덱스 | type | key | Extra | 실행시간 |
|------|------|--------|------|-----|-------|---------|
| 6-1 | 최신순 | 없음 | ALL | NULL | Using filesort | 123.19ms |
| 6-2 | 최신순 | (brand_id, created_at DESC) | ref | idx_products_brand_created | Using where | 0.10ms |
| 6-3 | 가격순 | 없음 | ALL | NULL | Using filesort | 108.23ms |
| 6-4 | 가격순 | (brand_id, price ASC) | ref | idx_products_brand_price | Using where | 0.10ms |

### [7] 사용자 좋아요 목록 — 좋아요한 시간순

| 번호 | 인덱스 | type(likes) | key | rows | Extra | 실행시간 |
|------|--------|-------------|-----|------|-------|---------|
| 7-1 | 없음 | ALL | NULL | 2,490,470 | Using filesort | 230.91ms |
| 7-2 | (member_id, created_at DESC) | ref | idx_likes_member_created | 241 | Using where | 0.48ms |
| 7-3 | (member_id, created_at DESC, product_id) | ref | idx_likes_member_created_product | 241 | Using where | 0.56ms |

### [8] 사용자 구매 목록

| 번호 | 인덱스 | type(orders) | type(order_items) | Extra | 실행시간 |
|------|--------|-------------|-------------------|-------|---------|
| 8-1 | 없음 | eq_ref | ALL | Using temporary; Using filesort | 0.30ms |
| 8-2 | orders: (member_id, created_at DESC) | ref | ALL | Using where; Using temporary; Using filesort | 0.13ms |
| 8-3 | + order_items: (order_id, product_id) | ref | ref | Using where | 0.15ms |

### [9] 전체 좋아요 순 (브랜드 필터 없음)

| 번호 | 인덱스 | OFFSET | type | key | rows | Extra | 실행시간 |
|------|--------|--------|------|-----|------|-------|---------|
| 9-1 | 없음 | 0 | ALL | NULL | 996,327 | Using filesort | 128.13ms |
| 9-2 | 없음 | 500,000 | ALL | NULL | 996,327 | Using filesort | 242.41ms |
| 9-3 | 단일(like_count DESC) | 0 | index | idx_products_like_count | 20 | Using where | 0.13ms |
| 9-4 | 단일(like_count DESC) | 500,000 | index | idx_products_like_count | 500,020 | Using where | 253.57ms |
| 9-5 | 커버링 | 0 | index | idx_products_like_covering | 20 | Using where | 0.25ms |
| 9-6 | 커버링 | 500,000 | ALL | NULL | 996,327 | Using filesort | 264.37ms |

> OFFSET이 크면 옵티마이저가 커버링 인덱스보다 풀스캔을 선택하는 경우 발생.
> OFFSET 500,000 = 25,000페이지(LIMIT 20 기준). 실서비스에서 발생하지 않는 수치.

### [10] 복합 필터 — 브랜드 + 가격 범위 + 좋아요 순

| 번호 | 인덱스 | type | key | Extra | 실행시간 |
|------|--------|------|-----|-------|---------|
| 10-1 | 없음 | ALL | NULL | Using filesort | 102.57ms |
| 10-2 | 단일(brand_id) | ref | idx_products_brand_id | Using filesort | 0.12ms |
| 10-3 | 복합(brand_id, like_count DESC) | ref | idx_products_brand_like | Using where | **0.10ms** |
| 10-4 | 복합(brand_id, price ASC) | range | idx_products_brand_price | Using index condition; Using filesort | 0.10ms |

> (brand_id, like_count DESC) 인덱스는 price range 조건이 있어도 filesort 제거 가능.
> MySQL이 brand_id=1 구간을 like_count 내림차순으로 읽으면서 price 조건을 행별로 필터링.

### [11] 재고 있는 상품만 — stocks JOIN + 좋아요 순

| 번호 | 인덱스 | type(p) | type(s) | Extra | 실행시간 |
|------|--------|---------|---------|-------|---------|
| 11-1 | 없음 | eq_ref | ALL | Using temporary; Using filesort | 541.31ms |
| 11-2 | products: (brand_id) | ref | ALL | Using temporary; Using filesort (hash join) | 0.16ms |
| 11-3 | products: (brand_id, like_count DESC) | ref | ALL | Using temporary; Using filesort (hash join) | 0.15ms |
| 11-4 | + stocks: (product_id, quantity) | ref | ref | Using where; Using index | **0.16ms** |

> hash join은 정렬 순서를 파괴 → (brand_id, like_count DESC) 단독으로 filesort 제거 불가.
> stocks에 (product_id, quantity) 인덱스 추가 시 nested loop join으로 전환 → filesort 제거.

### [12] 내가 좋아요 누른 상품 — 좋아요 수 순

| 번호 | 인덱스 | type(likes) | key | rows | Extra | 실행시간 |
|------|--------|-------------|-----|------|-------|---------|
| 12-1 | 없음 | ALL | NULL | 2,490,470 | Using temporary; Using filesort | 233.12ms |
| 12-2 | likes: (member_id) | ref | idx_likes_member_id | 241 | Using temporary; Using filesort | 1.23ms |
| 12-3 | + products: (like_count DESC) | ref | idx_likes_member_id | 241 | Using temporary; Using filesort | 0.75ms |
| 12-4 | + product_like_view JOIN | ref | idx_likes_member_id | 241 | Using temporary; Using filesort | 1.90ms |

### [13] 전체 최신순 (브랜드 필터 없음)

| 번호 | 인덱스 | OFFSET | type | key | rows | Extra | 실행시간 |
|------|--------|--------|------|-----|------|-------|---------|
| 13-1 | 없음 | 0 | ALL | NULL | 996,327 | Using filesort | 140.59ms |
| 13-2 | 없음 | 500,000 | ALL | NULL | 996,327 | Using filesort | 260.21ms |
| 13-3 | (created_at DESC) | 0 | index | idx_products_created_at | 20 | Using where | 0.28ms |
| 13-4 | (created_at DESC) | 500,000 | ALL | NULL | 996,327 | Using filesort | 277.05ms |

---

## 반정규화 vs 분리 테이블 설계 비교

### 읽기 성능

| 쿼리 | 반정규화 + 복합 인덱스 | 분리 테이블 + 단일 인덱스 | 차이 |
|------|----------------------|------------------------|------|
| 브랜드별 좋아요 순 OFFSET 0 | **0.08ms** (Using where, filesort 없음) | 0.12ms (Using filesort) | ~0.04ms |
| 브랜드별 좋아요 순 OFFSET 500K | **0.08ms** | 0.08ms | 동일 |
| 전체 좋아요 순 OFFSET 0 | 0.13ms | 동일 수준 | 동일 |
| 전체 좋아요 순 OFFSET 500K | 253ms~ (인덱스 스캔 한계) | 동일 수준 | 동일 |

> 차이가 0.04ms에 불과한 이유: brand_id 인덱스가 먼저 ~133건으로 줄여놓기 때문.
> 이후 filesort는 133건 대상 → 메모리 정렬 마이크로초 수준.

### 인덱스 수

| 테이블 | 반정규화 | 분리 테이블 |
|--------|---------|-----------|
| products | 4개 | 3개 |
| product_like_view | — | 1개 |
| likes | 1개 | 1개 |
| orders | 1개 | 1개 |
| order_items | 1개 | 1개 |
| **합계** | **8개** | **7개** (+테이블 1개) |

**반정규화 products 인덱스 (4개):**
- `(brand_id, like_count DESC)` — 브랜드별 좋아요 순
- `(like_count DESC, id, name, price, brand_id)` — 전체 좋아요 순 covering
- `(brand_id, created_at DESC)` — 최신순
- `(brand_id, price ASC)` — 가격순

**분리 테이블 products 인덱스 (3개):**
- `(brand_id)` — JOIN 필터용
- `(brand_id, created_at DESC)` — 최신순
- `(brand_id, price ASC)` — 가격순

### 쓰기 성능 (좋아요 1건 등록 기준)

| 작업 | 반정규화 | 분리 테이블 |
|------|---------|-----------|
| likes INSERT | ~0.5ms | ~0.5ms |
| like_count UPDATE | products 테이블 UPDATE | product_like_view 테이블 UPDATE |
| 인덱스 재정렬 | 2개 (brand+like, like covering) | 1개 (plv 인덱스) |
| 단건 합계 | ~1.5ms | ~1.0ms |

**동시 요청 시 락 경합 예상:**

| 동시 요청 | 반정규화 | 분리 테이블 | 이유 |
|----------|---------|-----------|------|
| 10명 | ~2ms | ~1ms | products는 상품 조회 API와 같은 테이블 → 락 경합 |
| 100명 | 10~30ms (스파이크) | ~2ms | products는 모든 API가 읽는 핫 테이블 |
| 1,000명 | 100ms+ 가능 | 5~10ms | product_like_view는 좋아요 연산만 접근 → 경합 범위 좁음 |

### 최종 결론

**선택: 분리 테이블 + 단일 인덱스 `(brand_id)`**

| 기준 | 반정규화 | 분리 테이블 |
|------|---------|-----------|
| 읽기 성능 | 미세하게 유리 (0.04ms) | 실질적으로 동일 |
| 쓰기 단건 | ~1.5ms | ~1.0ms |
| 고동시성 쓰기 | products 락 경합 위험 | 격리됨 |
| products 인덱스 수 | 4개 | 3개 |
| 구조 복잡도 | 단순 | 테이블 1개 추가 |

읽기 성능 차이는 실서비스에서 의미 없는 수준(0.04ms).
쓰기 시 products 테이블은 상품 조회 등 모든 API가 읽는 핫 테이블이므로,
like_count UPDATE가 섞이면 동시 사용자 증가에 따라 락 경합이 심화된다.
분리 테이블은 좋아요 연산의 락 범위를 product_like_view로 격리하여 이 문제를 근본적으로 해결한다.
