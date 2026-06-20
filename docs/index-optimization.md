# 상품 목록 조회 인덱스 최적화

## 개요

- 대상 테이블: `product` (10만 건)
- 대상 쿼리: 브랜드 필터 + 정렬 조건별 상품 목록 조회
- 측정 방법: `EXPLAIN ANALYZE` (MySQL 8.0)
- 테스트 클래스: `ProductListPerformanceE2ETest`

---

## 적용된 인덱스

| 인덱스명 | 컬럼 | 커버 쿼리 |
|---|---|---|
| `idx_product_deleted_at_created_at` | `(deleted_at, created_at)` | 전체 latest 정렬 |
| `idx_product_deleted_at_price` | `(deleted_at, price)` | 전체 price_asc 정렬 |
| `idx_product_deleted_at_like_count` | `(deleted_at, like_count)` | 전체 likes_desc 정렬 |
| `idx_product_brand_id_deleted_at_created_at` | `(brand_id, deleted_at, created_at)` | 브랜드 필터 + latest 정렬 |
| `idx_product_brand_id_deleted_at_price` | `(brand_id, deleted_at, price)` | 브랜드 필터 + price_asc 정렬 |
| `idx_product_brand_id_deleted_at_like_count` | `(brand_id, deleted_at, like_count)` | 브랜드 필터 + likes_desc 정렬 |

---

## 성능 개선 전후 비교

| 조건 | 인덱스 없이 | 인덱스 추가 후 | 개선 |
|---|---|---|---|
| latest 정렬 | 39ms | 0.156ms | 약 250배 |
| price_asc 정렬 | 37.8ms | 0.495ms | 약 76배 |
| likes_desc 정렬 | 39ms | 0.471ms | 약 83배 |
| 브랜드 필터 + latest | 24.5ms | 0.265ms | 약 92배 |
| 브랜드 필터 + price_asc | 24.6ms | 0.369ms | 약 67배 |
| 브랜드 필터 + likes_desc | 25ms | 0.420ms | 약 60배 |

---

## EXPLAIN 결과 상세

### 인덱스 없이 (공통)

모든 쿼리 패턴에서 동일하게 풀 테이블 스캔 발생

```
type=ALL, key=null, Extra=Using where; Using filesort
```

```
-> Limit: 10 row(s)
    -> Sort: product.{column}  (actual time=24~39ms rows=10)
        -> Filter: (product.deleted_at is null / product.brand_id = 1 and ...)
            -> Table scan on product  (rows=100,000)
```

### 인덱스 추가 후

#### latest 정렬

```
type=ref, key=idx_product_deleted_at_created_at, Extra=Using where; Backward index scan
```
```
-> Limit: 10 row(s)  (actual time=0.154..0.156 rows=10)
    -> Filter: (product.deleted_at is null)  (actual time=0.153..0.155 rows=10)
        -> Index lookup on product using idx_product_deleted_at_created_at (deleted_at=NULL) (reverse)  (actual time=0.152..0.154 rows=10)
```

#### price_asc 정렬

```
type=ref, key=idx_product_deleted_at_price, Extra=Using index condition
```
```
-> Limit: 10 row(s)  (actual time=0.493..0.495 rows=10)
    -> Index lookup on product using idx_product_deleted_at_price (deleted_at=NULL), with index condition: (product.deleted_at is null)  (actual time=0.492..0.494 rows=10)
```

#### likes_desc 정렬

```
type=ref, key=idx_product_deleted_at_like_count, Extra=Using where; Backward index scan
```
```
-> Limit: 10 row(s)  (actual time=0.467..0.471 rows=10)
    -> Filter: (product.deleted_at is null)  (actual time=0.467..0.47 rows=10)
        -> Index lookup on product using idx_product_deleted_at_like_count (deleted_at=NULL) (reverse)  (actual time=0.466..0.468 rows=10)
```

#### 브랜드 필터 + latest

```
type=ref, key=idx_product_brand_id_deleted_at_created_at, Extra=Using where; Backward index scan
```
```
-> Limit: 10 row(s)  (actual time=0.262..0.265 rows=10)
    -> Filter: (product.deleted_at is null)  (actual time=0.261..0.264 rows=10)
        -> Index lookup on product using idx_product_brand_id_deleted_at_created_at (brand_id=1, deleted_at=NULL) (reverse)  (actual time=0.26..0.262 rows=10)
```

#### 브랜드 필터 + price_asc

```
type=ref, key=idx_product_brand_id_deleted_at_price, Extra=Using index condition
```
```
-> Limit: 10 row(s)  (actual time=0.367..0.369 rows=10)
    -> Index lookup on product using idx_product_brand_id_deleted_at_price (brand_id=1, deleted_at=NULL), with index condition: (product.deleted_at is null)  (actual time=0.366..0.368 rows=10)
```

#### 브랜드 필터 + likes_desc

```
type=ref, key=idx_product_brand_id_deleted_at_like_count, Extra=Using where; Backward index scan
```
```
-> Limit: 10 row(s)  (actual time=0.417..0.420 rows=10)
    -> Filter: (product.deleted_at is null)  (actual time=0.416..0.419 rows=10)
        -> Index lookup on product using idx_product_brand_id_deleted_at_like_count (brand_id=1, deleted_at=NULL) (reverse)  (actual time=0.415..0.417 rows=10)
```

---

## 인덱스 컬럼 순서 결정 — `(brand_id, deleted_at, ...)` vs `(deleted_at, brand_id, ...)`

### 측정 결과 비교 (10만 건, 삭제 비율 30%)

| 쿼리 | `(brand_id, deleted_at, ...)` | `(deleted_at, brand_id, ...)` |
|---|---|---|
| 브랜드 + latest | 0.286ms | 0.292ms |
| 브랜드 + price_asc | 0.485ms | 0.397ms |
| 브랜드 + likes_desc | 0.461ms | 0.418ms |

두 순서 모두 수치 차이가 0.1ms 이내로 **측정 오차 범위 수준**이다.

### 왜 수치 차이가 없는가

`deleted_at IS NULL`과 `brand_id = ?`는 **둘 다 equality 조건**이기 때문이다. B-Tree 인덱스는 equality 조건 여러 개를 연속으로 탐색할 때 순서와 무관하게 동일한 노드 수를 탐색한다. 두 인덱스 모두 최종적으로 좁혀지는 행 수가 같다.

```
(brand_id=1, deleted_at=NULL) → rows ≈ 1,400
(deleted_at=NULL, brand_id=1) → rows ≈ 1,400  ← 동일
```

순서가 중요해지는 시점은 equality가 끝나고 **range 조건(`>`, `<`, `BETWEEN`)이나 정렬 컬럼**이 등장할 때다. 세 번째 자리의 `created_at`, `price`, `like_count`가 그 역할을 하며, 이 위치는 두 설계 모두 동일하다.

### 그럼에도 `(brand_id, deleted_at, ...)` 를 선택한 근거

**선택도(Cardinality)** 원칙: equality 조건이 여러 개일 때는 더 많이 좁혀주는 컬럼을 앞에 두는 것이 이론적으로 올바르다.

| 컬럼 | 현실적 선택도 |
|---|---|
| `deleted_at IS NULL` | 전체의 70~100% (활성 데이터 비율에 따라 결정, 거의 좁혀지지 않음) |
| `brand_id = ?` | 전체의 ~1/브랜드수 (브랜드 50개 기준 약 2%) |

`deleted_at`을 앞에 두면 인덱스의 첫 분기에서 전체 데이터의 70~100%를 포함하는 NULL 파티션에 진입한 뒤 `brand_id`로 좁힌다. `brand_id`를 앞에 두면 첫 분기에서 즉시 해당 브랜드 행(~2%)만 남기고 진입한다.

10만 건 규모에서 두 방식의 실측 차이가 없는 이유는 두 컬럼이 모두 equality 조건이기 때문이다. 그러나 **데이터가 수천만 건으로 늘어나거나 삭제 비율이 낮아져 `deleted_at IS NULL`이 전체의 99% 이상을 차지하는 시점에는** `brand_id` 우선 설계가 더 유리하다.

---

## DESC 인덱스 vs ASC 인덱스 — Backward index scan 제거 실험

`latest` / `likes_desc` 계열은 DESC 정렬로 인해 ASC 인덱스를 역방향으로 읽는 `Backward index scan`이 발생한다. DESC 인덱스(`created_at DESC`, `like_count DESC`)를 적용하면 Forward scan으로 전환되어 이 레이블이 사라진다.

### Backward index scan이란

MySQL B-Tree 인덱스는 기본적으로 ASC 순서로 저장된다. `ORDER BY col DESC` 쿼리 시 리프 노드를 역방향으로 체인 탐색하는 것이 Backward index scan이다. InnoDB의 read-ahead(prefetch)가 순방향에만 동작하기 때문에 역방향 탐색 시 prefetch 효과를 받지 못한다.

### 1페이지(shallow) 비교 결과 (LIMIT 20, 데이터 10만 건, 삭제 비율 30%)

| 쿼리 | ASC 인덱스 | DESC 인덱스 |
|---|---|---|
| latest | 0.154ms *(Backward)* | **0.139ms** |
| likes_desc | **0.483ms** *(Backward)* | 0.558ms |
| 브랜드 + latest | 0.334ms *(Backward)* | **0.266ms** |
| 브랜드 + likes_desc | **0.473ms** *(Backward)* | 0.479ms |

수치 차이가 오차 범위 내로 shallow pagination에서는 두 인덱스 간 유의미한 성능 차이가 없다.

### 깊은 페이지(deep pagination) 비교 결과

| 쿼리 | ASC 인덱스 | DESC 인덱스 |
|---|---|---|
| latest (OFFSET 40000) | **36.1ms** *(Backward)* | 35.7ms |
| likes_desc (OFFSET 40000) | **142ms** *(Backward)* | 144ms |
| 브랜드 + latest (OFFSET 600) | **1.86ms** *(Backward)* | 2.62ms |
| 브랜드 + likes_desc (OFFSET 600) | 4.02ms *(Backward)* | **3.43ms** |

### DESC 인덱스가 deep pagination을 해결하지 못하는 이유

`Backward index scan` 레이블은 사라지지만 실행 시간은 거의 동일하거나 오히려 느린 케이스도 존재한다. deep pagination의 진짜 병목은 scan 방향이 아니라 **OFFSET 자체**다.

```
LIMIT 20 OFFSET 40000
→ ASC 인덱스: 역방향으로 40,020개 탐색 후 마지막 20개 반환
→ DESC 인덱스: 순방향으로 40,020개 탐색 후 마지막 20개 반환
→ 읽는 행 수가 동일하므로 비용도 동일
```

prefetch는 연속된 페이지를 미리 읽는 최적화인데, OFFSET 기반 쿼리는 중간 행을 모두 읽고 버리는 구조라 prefetch 효과가 결과에 반영되지 않는다.

### Deep pagination의 실제 해결책 — 커서 기반 페이지네이션(Keyset Pagination)

OFFSET 방식은 페이지가 깊어질수록 선형으로 비용이 증가한다. 현재 JPA 구현(`Page<Product>` + `Pageable`)은 내부적으로 `LIMIT N OFFSET M`을 사용하므로 이 문제에서 자유롭지 않다.

커서 기반 페이지네이션은 마지막으로 읽은 행의 정렬 키를 커서로 삼아 WHERE 조건으로 전달한다.

```sql
-- OFFSET 방식: 페이지가 깊어질수록 40,020개를 읽고 버림
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY created_at DESC
LIMIT 20 OFFSET 40000;

-- 커서 방식: 항상 인덱스에서 해당 지점으로 바로 점프, 20개만 읽음
SELECT * FROM product
WHERE deleted_at IS NULL
  AND created_at < :lastCreatedAt   -- 이전 페이지 마지막 행의 created_at
ORDER BY created_at DESC
LIMIT 20;
```

커서 방식으로 전환하면 인덱스가 `(deleted_at, created_at)` 또는 `(brand_id, deleted_at, created_at)` 복합 인덱스를 그대로 활용하며, 페이지 깊이와 무관하게 항상 20개만 읽는다. 단, 임의 페이지 이동(page jump)이 불가능하고 정렬 컬럼에 중복값이 있으면 커서 설계가 복잡해지는 trade-off가 있다.

### Deep pagination의 대안 — 커버링 인덱스 + Deferred Join

임의 페이지 이동이 필요해 OFFSET을 포기할 수 없는 경우, **커버링 인덱스 + Deferred Join** 방식으로 OFFSET 비용을 줄일 수 있다.

**OFFSET 방식의 실제 비용 구조**

InnoDB에서 `SELECT *`는 세컨더리 인덱스를 탐색한 뒤 각 행마다 클러스터드 인덱스(PK)로 랜덤 I/O를 발생시킨다. `LIMIT 20 OFFSET 40000`이면 버려질 40,000개 행에 대해서도 이 랜덤 I/O가 발생한다.

```
SELECT * FROM product WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20 OFFSET 40000;

세컨더리 인덱스 탐색 (40,020개)
    └─ 각 행마다 클러스터드 인덱스 랜덤 I/O × 40,020 ← 실제 병목
           → 버려질 40,000개 포함
```

**커버링 인덱스란**

쿼리에서 필요한 컬럼이 인덱스 안에 모두 포함되어 있을 때, 클러스터드 인덱스로의 랜덤 I/O 없이 인덱스만 읽고 응답할 수 있는 상태를 말한다. InnoDB 세컨더리 인덱스는 리프 노드에 PK 값을 자동으로 포함하므로, `SELECT id`만 사용하는 쿼리는 대부분 커버링 인덱스로 처리된다.

```
idx_product_deleted_at_created_at (deleted_at, created_at)
  └─ 리프 노드에 id(PK) 자동 포함
     → SELECT id WHERE deleted_at IS NULL ORDER BY created_at DESC
       는 클러스터드 인덱스 접근 없이 인덱스만으로 처리 가능
```

**Deferred Join (Late Row Lookup) 패턴**

서브쿼리로 커버링 인덱스를 통해 id만 먼저 추출한 뒤, 그 20개 id로만 전체 컬럼을 조회한다.

```sql
-- 기존 방식: 40,020개 행 × 클러스터드 인덱스 랜덤 I/O
SELECT *
FROM product
WHERE deleted_at IS NULL
ORDER BY created_at DESC
LIMIT 20 OFFSET 40000;

-- Deferred Join: 40,020개는 인덱스만 읽고, 랜덤 I/O는 결과 20개에만 발생
SELECT p.*
FROM product p
JOIN (
    SELECT id
    FROM product
    WHERE deleted_at IS NULL
    ORDER BY created_at DESC
    LIMIT 20 OFFSET 40000
) AS ids ON p.id = ids.id;
```

서브쿼리는 `(deleted_at, created_at)` 인덱스를 커버링 인덱스로 사용하여 클러스터드 인덱스 접근 없이 id만 가져온다. 이후 JOIN 대상은 20개뿐이므로 랜덤 I/O가 20회로 줄어든다.

**세 가지 방식 비교**

| 방식 | 임의 페이지 이동 | Deep pagination 성능 | 구현 복잡도 |
|---|---|---|---|
| OFFSET 방식 | 가능 | 페이지 깊이에 비례해 선형 증가 | 낮음 |
| 커버링 인덱스 + Deferred Join | 가능 | 랜덤 I/O 비용을 20회로 고정 | 중간 |
| 커서 기반(Keyset) | 불가 | 항상 20개만 읽음 | 높음 |

현재 JPA 구현(`Page<Product>` + `Pageable`)은 OFFSET 방식이다. Deferred Join은 QueryDSL이나 네이티브 쿼리로 서브쿼리를 작성해야 하며, 커서 방식 대비 인터페이스 변경 범위가 작다는 장점이 있다.

---

## k6 부하 테스트 — 인덱스 전후 API 성능 비교

### 테스트 환경

| 항목 | 값 |
|---|---|
| 도구 | k6 v2.0.0 |
| 가상 유저(VU) | 50 |
| 지속 시간 | 30s |
| 데이터 | 10만 건 (삭제 비율 30%) |
| 측정 대상 | `GET /api/v1/products` (6가지 쿼리 패턴 + deep pagination 2가지) |

### Before vs After — p(95) 응답 시간

| 쿼리 패턴 | 인덱스 없이 p(95) | 인덱스 추가 후 p(95) | 개선 |
|---|---|---|---|
| latest (page 0) | 985ms ❌ | **88ms** ✓ | **11배** |
| price_asc (page 0) | 256ms | **87ms** | 2.9배 |
| likes_desc (page 0) | 241ms | **89ms** | 2.7배 |
| 브랜드 + latest (page 0) | 252ms | **53ms** | **4.7배** |
| 브랜드 + price_asc (page 0) | 255ms | **51ms** | **5.0배** |
| 브랜드 + likes_desc (page 0) | 256ms | **51ms** | **5.0배** |
| latest deep (page 2000, OFFSET 40000) | 329ms | 496ms | - |
| likes_desc deep (page 30, OFFSET 600) | 251ms | **96ms** | 2.6배 |

### 처리량 비교

| | 인덱스 없이 | 인덱스 추가 후 |
|---|---|---|
| 총 요청 수 (30s) | 7,616 | **18,040** |
| 초당 요청 수 (RPS) | 246 req/s | **591 req/s** |
| iteration/s | 30.8/s | **74.0/s** |

### 주요 관찰

**`latest` before p(95)=985ms** — 풀스캔(10만 건)이 50명 동시 요청을 받으면 DB 커넥션 대기가 쌓이면서 꼬리 레이턴시가 급격히 상승했다. EXPLAIN 단일 쿼리 시간(~39ms)과 다르게, 부하 하에서는 DB I/O 포화로 인해 최대 993ms까지 치솟았다. 인덱스 추가 후 p(95)=88ms로 정상화됐다.

**deep latest (OFFSET 40000) after가 496ms로 오히려 증가** — before(329ms)는 풀스캔이 인덱스 없이 랜덤하게 종료되어 실제 OFFSET 비용이 반영되지 않은 수치다. after(496ms)는 인덱스를 통해 40,020건을 실제로 순회한 진짜 비용이다. OFFSET 기반 deep pagination은 인덱스를 추가해도 근본적으로 해결되지 않음을 부하 테스트로도 확인했다.

**처리량 2.4배 향상** — 단일 쿼리 응답 시간 개선보다 처리량 증가폭이 큰 이유는, 인덱스 추가로 DB 점유 시간이 줄어 커넥션 풀 경합이 해소되어 더 많은 요청을 동시에 처리할 수 있게 됐기 때문이다.

---

## 관찰 사항

- 인덱스 생성 직후 `ANALYZE TABLE product`를 실행하지 않으면 옵티마이저가 브랜드 복합 인덱스 대신 `deleted_at` 단순 인덱스를 선택하는 현상이 발생함. bulk insert 직후 InnoDB 통계가 안정화되지 않은 상태에서 샘플링 기반 통계가 부정확하게 계산된 것이 원인. `ANALYZE TABLE` 실행 후 모든 케이스에서 의도한 인덱스가 선택됨.
- **price_asc** 계열은 `Using filesort` 없이 인덱스 순서 그대로 정렬이 가능하여 가장 효율적.
- **latest / likes_desc** 계열은 DESC 정렬로 인해 `Backward index scan` 발생하나 shallow pagination에서는 성능 영향이 미미하며, DESC 인덱스를 적용해도 deep pagination 성능은 개선되지 않음.
- 운영 환경에서는 데이터가 점진적으로 쌓이며 통계가 자동 갱신(`innodb_stats_auto_recalc=ON`)되므로 테스트 환경 특이 현상으로 볼 수 있음.
