# 상품 조회 인덱스 튜닝 결과

데이터: 상품 100만건 (활성 90만건), 브랜드 20개  
벤치마크 쿼리: `SELECT *` (실제 애플리케이션 기준)

---

## 인덱스 없음 (BEFORE)

| 쿼리 | type | key | rows | Extra | avg_ms |
|---|---|---|---|---|---|
| ① 전체+최신순 | ALL | — | 902,403 | Using where; Using filesort | 604.16 |
| ② 전체+좋아요순 | ALL | — | 902,403 | Using where; Using filesort | 594.10 |
| ③ 전체+가격순 | ALL | — | 902,403 | Using where; Using filesort | 583.54 |
| ④ 브랜드+최신순 | ref | FK(brand_id) | 103,374 | Using index condition; Using where; Using filesort | 311.59 |
| ⑤ 브랜드+좋아요순 | ref | FK(brand_id) | 103,374 | Using index condition; Using where; Using filesort | 317.99 |
| ⑥ 브랜드+가격순 | ref | FK(brand_id) | 103,374 | Using index condition; Using where; Using filesort | 356.11 |

---

## 2개 인덱스 — 필터만 `(deleted_at)`, `(brand_id, deleted_at)`

```sql
ALTER TABLE products
    ADD INDEX idx_products_deleted_at    (deleted_at),
    ADD INDEX idx_products_brand_deleted (brand_id, deleted_at);
```

**인덱스 정의**

| 인덱스명 | 컬럼 |
|---|---|
| idx_products_deleted_at | (deleted_at) |
| idx_products_brand_deleted | (brand_id, deleted_at) |

| 쿼리 | type | key | rows | Extra | avg_ms |
|---|---|---|---|---|---|
| ① 전체+최신순 | ref | idx_products_deleted_at | 451,201 | Using index condition; Using filesort | 2,649.03 |
| ② 전체+좋아요순 | ref | idx_products_deleted_at | 451,201 | Using index condition; Using filesort | 2,511.25 |
| ③ 전체+가격순 | ref | idx_products_deleted_at | 451,201 | Using index condition; Using filesort | 2,426.60 |
| ④ 브랜드+최신순 | ref | idx_products_brand_deleted | 92,882 | Using index condition; Using filesort | 276.93 |
| ⑤ 브랜드+좋아요순 | ref | idx_products_brand_deleted | 92,882 | Using index condition; Using filesort | 278.36 |
| ⑥ 브랜드+가격순 | ref | idx_products_brand_deleted | 92,882 | Using index condition; Using filesort | 283.46 |

---

## 6개 인덱스 — 필터+정렬

```sql
ALTER TABLE products
    ADD INDEX idx_products_deleted_latest       (deleted_at, created_at DESC),
    ADD INDEX idx_products_deleted_likes        (deleted_at, like_count DESC),
    ADD INDEX idx_products_deleted_price        (deleted_at, price ASC),
    ADD INDEX idx_products_brand_deleted_latest (brand_id, deleted_at, created_at DESC),
    ADD INDEX idx_products_brand_deleted_likes  (brand_id, deleted_at, like_count DESC),
    ADD INDEX idx_products_brand_deleted_price  (brand_id, deleted_at, price ASC);
```

**인덱스 정의**

| 인덱스명 | 컬럼 |
|---|---|
| idx_products_deleted_latest | (deleted_at, created_at DESC) |
| idx_products_deleted_likes | (deleted_at, like_count DESC) |
| idx_products_deleted_price | (deleted_at, price ASC) |
| idx_products_brand_deleted_latest | (brand_id, deleted_at, created_at DESC) |
| idx_products_brand_deleted_likes | (brand_id, deleted_at, like_count DESC) |
| idx_products_brand_deleted_price | (brand_id, deleted_at, price ASC) |

| 쿼리 | type | key | rows | Extra | avg_ms |
|---|---|---|---|---|---|
| ① 전체+최신순 | ref | idx_products_deleted_latest | 451,201* | Using index condition | 0.12 |
| ② 전체+좋아요순 | ref | idx_products_deleted_likes | 451,201* | Using index condition | 0.14 |
| ③ 전체+가격순 | ref | idx_products_deleted_price | 451,201* | Using index condition | 0.11 |
| ④ 브랜드+최신순 | ref | idx_products_brand_deleted_latest | 95,326* | Using index condition | 0.17 |
| ⑤ 브랜드+좋아요순 | ref | idx_products_brand_deleted_likes | 95,326* | Using index condition | 0.15 |
| ⑥ 브랜드+가격순 | ref | idx_products_brand_deleted_price | 95,326* | Using index condition | 0.14 |

\* rows: optimizer 추정치. LIMIT 20 early termination으로 실제 읽은 행은 20개.

---

## 2개 인덱스가 인덱스 없음보다 느린 이유

`(deleted_at)` 단일 인덱스의 선택도가 90% (900K건 해당).

- **인덱스 없음**: 테이블 순차 읽기 → InnoDB prefetch 작동 → 1M행 연속 I/O → filesort
- **2개 인덱스**: `deleted_at IS NULL` ref 스캔 → **900K번 랜덤 I/O** (클러스터드 테이블 점프) → filesort
- **6개 인덱스**: 정렬 컬럼 포함 → LIMIT 20 early termination → **20번만 읽고 종료** → filesort 없음

2개 인덱스는 전체 qualifying rows를 정렬 전에 모두 읽어야 하므로 900K 랜덤 I/O 발생.
순차 I/O(인덱스 없음 ~590ms)보다 랜덤 I/O(2개 인덱스 ~2,500ms)가 훨씬 비싸다.

일반적으로 선택도가 90% 이상이면 풀스캔이 유리하다고 알려져 있지만, 옵티마이저는 선택도만으로 플랜을 결정하지 않는다. `read_cost + eval_cost`를 합산한 전체 비용을 비교해 낮은 쪽을 선택한다. 이 케이스에서 ref cost(86,924) < ALL cost(104,175)로 계산되어 옵티마이저가 ref를 선택했지만, MySQL의 `io_block_read_cost = 1.0`이 sequential/random I/O를 동일 단가로 취급하면서 실제 성능이 역전됐다.

---

## INSERT 성능 변화

1,000건 단건 INSERT 기준:

| 상태 | per_insert_ms | total_ms (1,000건) | 인덱스 없음 대비 |
|---|---|---|---|
| 인덱스 없음 | 5.611ms | 5,611ms | — |
| 2개 인덱스 | 5.690ms | 5,690ms | +1.4% |
| 6개 인덱스 | 5.893ms | 5,892ms | +5.0% |
