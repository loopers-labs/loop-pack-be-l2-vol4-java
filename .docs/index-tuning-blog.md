# 인덱스를 추가했더니 오히려 더 느려졌다 — MySQL 옵티마이저가 틀린 선택을 하는 이유

## 실험 환경

- MySQL 8.0, InnoDB
- 상품 테이블 100만 건 (활성: 900,000건 / 삭제: 100,000건)
- 브랜드 20개 (상품당 균등 분포, 브랜드당 약 50,000건)
- 벤치마크: SELECT * 기준, 콜드캐시 1회 제외 후 9회 평균

테스트 쿼리 6종:

```sql
-- ① 전체 + 최신순
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;

-- ② 전체 + 좋아요순
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY like_count DESC LIMIT 20;

-- ③ 전체 + 가격순
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY price ASC LIMIT 20;

-- ④ 브랜드 + 최신순
SELECT * FROM products WHERE brand_id = ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;

-- ⑤ 브랜드 + 좋아요순
SELECT * FROM products WHERE brand_id = ? AND deleted_at IS NULL ORDER BY like_count DESC LIMIT 20;

-- ⑥ 브랜드 + 가격순
SELECT * FROM products WHERE brand_id = ? AND deleted_at IS NULL ORDER BY price ASC LIMIT 20;
```

---

## Step 0 — 인덱스 없음 (FK만 존재)

| 쿼리 | type | key | rows | filtered | Extra | avg_ms |
|---|---|---|---|---|---|---|
| ① 전체+최신순 | ALL | — | 902,403 | 10.0 | Using where; Using filesort | 604 |
| ② 전체+좋아요순 | ALL | — | 902,403 | 10.0 | Using where; Using filesort | 594 |
| ③ 전체+가격순 | ALL | — | 902,403 | 10.0 | Using where; Using filesort | 583 |
| ④ 브랜드+최신순 | ref | FK(brand_id) | 103,374 | 10.0 | Using index condition; Using where; Using filesort | 311 |
| ⑤ 브랜드+좋아요순 | ref | FK(brand_id) | 103,374 | 10.0 | Using index condition; Using where; Using filesort | 317 |
| ⑥ 브랜드+가격순 | ref | FK(brand_id) | 103,374 | 10.0 | Using index condition; Using where; Using filesort | 356 |

---

## Step 1 — 2개 인덱스 추가 (필터 전용)

```sql
ALTER TABLE products
    ADD INDEX idx_products_deleted_at    (deleted_at),
    ADD INDEX idx_products_brand_deleted (brand_id, deleted_at);
```

| 쿼리 | type | key | rows | filtered | Extra | avg_ms |
|---|---|---|---|---|---|---|
| ① 전체+최신순 | ref | idx_products_deleted_at | 451,201 | 100.0 | Using index condition; Using filesort | 2,649 |
| ② 전체+좋아요순 | ref | idx_products_deleted_at | 451,201 | 100.0 | Using index condition; Using filesort | 2,511 |
| ③ 전체+가격순 | ref | idx_products_deleted_at | 451,201 | 100.0 | Using index condition; Using filesort | 2,426 |
| ④ 브랜드+최신순 | ref | idx_products_brand_deleted | 92,882 | 100.0 | Using index condition; Using filesort | 276 |
| ⑤ 브랜드+좋아요순 | ref | idx_products_brand_deleted | 92,882 | 100.0 | Using index condition; Using filesort | 278 |
| ⑥ 브랜드+가격순 | ref | idx_products_brand_deleted | 92,882 | 100.0 | Using index condition; Using filesort | 283 |

**① ② ③이 인덱스 없을 때보다 4배 이상 느려졌다.** (604ms → 2,649ms)

---

## 왜 인덱스를 추가했는데 더 느려졌나

### 1. 선택도 문제 — 90% 활성은 인덱스에 불리하다

"선택도(Selectivity)"는 인덱스로 얼마나 걸러낼 수 있냐는 지표다.

`deleted_at IS NULL`이 90만 건 = 전체의 90%다. 인덱스를 타면 그 90만 건을 전부 가져와야 한다. 일반적으로 "선택도가 낮으면(= 걸러지는 게 별로 없으면) 풀스캔이 낫다"고 알려져 있다. 실제로도 그렇다. **그런데 옵티마이저는 풀스캔을 선택하지 않았다.**

### 2. 옵티마이저는 선택도로 결정하지 않는다 — cost 모델로 결정한다

옵티마이저는 선택도 자체를 보는 게 아니라 `read_cost + eval_cost`를 계산해서 낮은 쪽을 선택한다.

`optimizer_trace`를 켜면 내부 비용 계산을 볼 수 있다:

```sql
SET optimizer_trace = 'enabled=on';
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;
SELECT * FROM information_schema.OPTIMIZER_TRACE;
SET optimizer_trace = 'enabled=off';
```

trace 결과의 `considered_access_paths`:

```json
{
  "access_type": "ref",
  "index": "idx_products_deleted_at",
  "rows": 451201,
  "cost": 86924
},
{
  "access_type": "scan",
  "cost": 104175,
  "chosen": false
}
```

옵티마이저는 ref(86,924) < ALL(104,175)로 계산하고 ref를 선택했다. **숫자로는 ref가 더 저렴했다.**

그런데 실제 결과는 ALL(604ms) < ref(2,649ms).

### 3. 핵심은 rows 수가 아니라 랜덤 I/O다

`idx_products_deleted_at`는 `(deleted_at)` 단일 컬럼 인덱스다. 인덱스 트리에서 `deleted_at IS NULL` 조건에 맞는 90만 건을 찾은 다음, **각 행마다 실제 데이터를 가져오기 위해 클러스터드 인덱스(PK)를 조회**해야 한다. 이걸 더블 룩업(Double Lookup)이라 한다.

```
인덱스 리프 노드: deleted_at=NULL → PK 값
클러스터드 인덱스: PK → 실제 행 데이터 (SELECT *)
```

90만 건이면 이 과정이 90만 번 반복된다. 그리고 PK 접근은 디스크 여기저기 흩어진 페이지를 읽는 **랜덤 I/O**다.

반면 풀스캔(ALL)은 테이블 페이지를 순서대로 읽는 **순차 I/O**다. InnoDB는 순차 I/O에 prefetch(미리 읽기)가 작동한다.

```
풀스캔:   1M 행 순차 I/O    → prefetch 작동 → 604ms
ref 스캔: 90만 번 랜덤 I/O → prefetch 없음 → 2,649ms
```

### 4. MySQL cost 모델이 랜덤 I/O를 무시한다

MySQL의 기본 cost 파라미터:

```sql
SELECT * FROM mysql.server_cost WHERE cost_name = 'io_block_read_cost';
-- io_block_read_cost = 1.0
```

**순차 I/O와 랜덤 I/O에 동일한 단가(1.0)를 부여한다.** 실제 하드웨어에서 랜덤 I/O는 순차 I/O보다 수십 배 느릴 수 있지만, cost 모델은 이를 구분하지 않는다.

그래서 옵티마이저는:
- ref: 인덱스 경로 비용 = 86,924 (90만 번 랜덤 I/O가 cost에 제대로 반영되지 않음)
- ALL: 902K 행 순차 읽기 비용 = 104,175

ref가 저렴해 보였고, 잘못된 선택을 했다.

### 5. 통계 메커니즘 — 왜 ref rows는 451K인가

옵티마이저가 ref의 rows를 추정하는 방법과 ALL을 추정하는 방법이 다르다:

| 접근 방식 | 통계 메커니즘 |
|---|---|
| ref (인덱스 접근) | **index dive** — 인덱스 B-tree를 실제로 탐색해서 해당 범위 행 수 추정. 정확도 높음 |
| ALL (풀스캔) | **column 통계 샘플링** — 인덱스 없는 컬럼은 기본 추정값 사용. 정확도 낮음 |

두 경로가 서로 다른 메커니즘으로 추정되면서 cost 비교 자체가 기울어진 운동장이 된다.

> **filtered 컬럼 해석:**
> Step 0: `filtered=10.0` — `deleted_at`에 인덱스가 없으니 서버 계층에서 IS NULL 필터를 직접 적용. MySQL이 통계 없이 기본값으로 추정한 수치.
> Step 1: `filtered=100.0` — `(deleted_at)` 인덱스 키 자체가 WHERE 조건을 처리. 인덱스 스캔 결과인 451K 행이 이미 `deleted_at IS NULL`인 것들만 → 추가 서버 필터 없음 → 100%.
> `filtered`는 인덱스 접근 후 **남은 WHERE 조건 통과율**이다. 인덱스가 조건을 전부 커버하면 100%.

---

## Step 2 — 6개 인덱스 (필터 + 정렬 컬럼 포함)

```sql
ALTER TABLE products
    ADD INDEX idx_products_deleted_latest       (deleted_at, created_at DESC),
    ADD INDEX idx_products_deleted_likes        (deleted_at, like_count DESC),
    ADD INDEX idx_products_deleted_price        (deleted_at, price ASC),
    ADD INDEX idx_products_brand_deleted_latest (brand_id, deleted_at, created_at DESC),
    ADD INDEX idx_products_brand_deleted_likes  (brand_id, deleted_at, like_count DESC),
    ADD INDEX idx_products_brand_deleted_price  (brand_id, deleted_at, price ASC);
```

| 쿼리 | type | key | rows | filtered | Extra | avg_ms |
|---|---|---|---|---|---|---|
| ① 전체+최신순 | ref | idx_products_deleted_latest | 451,201* | 100.0 | Using index condition | 0.12 |
| ② 전체+좋아요순 | ref | idx_products_deleted_likes | 451,201* | 100.0 | Using index condition | 0.14 |
| ③ 전체+가격순 | ref | idx_products_deleted_price | 451,201* | 100.0 | Using index condition | 0.11 |
| ④ 브랜드+최신순 | ref | idx_products_brand_deleted_latest | 95,326* | 100.0 | Using index condition | 0.17 |
| ⑤ 브랜드+좋아요순 | ref | idx_products_brand_deleted_likes | 95,326* | 100.0 | Using index condition | 0.15 |
| ⑥ 브랜드+가격순 | ref | idx_products_brand_deleted_price | 95,326* | 100.0 | Using index condition | 0.14 |

\* rows는 optimizer 추정치. LIMIT 20 early termination으로 실제 읽은 행은 20개.

604ms → **0.12ms**. 약 5,000배 빨라졌다.

### LIMIT early termination

인덱스가 `(deleted_at, created_at DESC)` 순서로 구성되면, `deleted_at IS NULL` 조건으로 시작하는 구간이 **이미 `created_at DESC` 순서로 정렬된 상태**다.

```
인덱스 순서:
  [deleted_at=NULL, created_at=최신] → PK
  [deleted_at=NULL, created_at=...] → PK
  ...
```

`LIMIT 20`이면 인덱스 앞에서 20개만 읽고 바로 멈춘다. 90만 건을 전부 읽고 filesort할 필요가 없다. 더블 룩업도 20번이 전부다.

rows=451,201로 표시되지만, 실제 접근한 행은 20개다.

---

## 3단계 비교 요약

| 상태 | ① 전체+최신순 | ④ 브랜드+최신순 |
|---|---|---|
| 인덱스 없음 | 604ms | 311ms |
| 2개 인덱스 | 2,649ms (**4.4배 느림**) | 276ms |
| 6개 인덱스 | 0.12ms (**5,000배 빠름**) | 0.17ms |

---

## 교훈

**1. EXPLAIN만 보면 착각하기 쉽다**

EXPLAIN은 옵티마이저가 선택한 플랜 하나만 보여준다. ref를 탔다는 것, rows=451K라는 것은 알 수 있지만, 왜 ALL 대신 ref를 선택했는지, 두 경로의 cost를 왜 그렇게 계산했는지는 보이지 않는다. "ref 탔으니까 인덱스 잘 탄다"고 결론내리면 실제로 4배 느린 쿼리를 배포하게 된다.

**2. 선택도가 낮다고 반드시 풀스캔이 빠른 건 아니다 — 하지만 옵티마이저가 잘못 선택할 수 있다**

선택도 90%는 인덱스로 걸러지는 게 거의 없다는 의미다. 그러나 옵티마이저는 선택도 자체가 아니라 `read_cost + eval_cost` 합산값으로 결정한다. 이 cost 계산이 랜덤 I/O를 제대로 반영하지 못하면, 선택도가 나빠도 인덱스를 타는 플랜을 선택한다.

**3. rows가 많다고 느린 게 아니다 — 랜덤 I/O 횟수가 진짜다**

rows=451K를 보고 "많이 읽네"라고 생각할 수 있다. 진짜 문제는 그 접근이 순차냐 랜덤이냐다. 90만 번 랜덤 I/O(더블 룩업)는 1M 번 순차 I/O보다 훨씬 비싸다.

**4. 옵티마이저가 왜 그 선택을 했는지 알려면 optimizer_trace가 필요하다**

```sql
SET optimizer_trace = 'enabled=on';
SELECT * FROM products WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;
SELECT * FROM information_schema.OPTIMIZER_TRACE;
SET optimizer_trace = 'enabled=off';
```

`considered_access_paths` 안에서 각 접근 경로별 cost를 직접 비교할 수 있다. "옵티마이저가 잘못된 선택을 했다"는 결론은 EXPLAIN이 아니라 trace에서 cost 값을 보고 내려야 한다.

**5. 인덱스 설계는 WHERE만이 아니라 ORDER BY까지 포함해야 한다**

`(deleted_at)`만 있으면 필터는 되지만 정렬은 별도로 해야 한다. `(deleted_at, created_at DESC)`처럼 정렬 컬럼을 포함하면 인덱스 순서 자체가 정렬을 대신하고, LIMIT early termination이 작동해서 실제 읽는 행이 극적으로 줄어든다.

---

## 부록 — 인덱스 추가에 따른 INSERT 성능 변화

인덱스를 추가하면 쓰기 시 인덱스 트리도 함께 갱신해야 하므로 INSERT 비용이 증가한다.

1,000건 단건 INSERT 기준:

| 상태 | per_insert_ms | total_ms (1,000건) | 인덱스 없음 대비 |
|---|---|---|---|
| 인덱스 없음 | 5.611ms | 5,611ms | — |
| 2개 인덱스 | 5.690ms | 5,690ms | +1.4% |
| 6개 인덱스 | 5.893ms | 5,892ms | +5.0% |

인덱스를 6개 추가해도 INSERT 속도 저하는 **약 5%** 수준이다.

반면 조회 성능은 인덱스 없음 대비:

| 쿼리 | 인덱스 없음 | 6개 인덱스 | 개선율 |
|---|---|---|---|
| ① 전체+최신순 | 604ms | 0.12ms | **약 5,000배** |
| ④ 브랜드+최신순 | 311ms | 0.17ms | **약 1,800배** |

조회가 압도적으로 많은 서비스에서 쓰기 5% 손해는 조회 5,000배 이득으로 충분히 상쇄된다. 단, 쓰기가 극단적으로 많은 워크로드(대량 bulk insert, CDC 파이프라인 등)에서는 인덱스 수가 병목이 될 수 있으므로 트레이드오프를 측정하고 결정해야 한다.
