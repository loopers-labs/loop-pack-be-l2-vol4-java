# [Stage 3] 인덱스 (단일 → 복합 → 커버링) 측정 보고서

> 환경·시나리오 정의·측정 규약은 `00-setup.md`. 직전 단계는 `02-denormalization.md`.

## 이번 단계 한눈에

- **무엇을 바꿨나**: 비정규화로 좋아요 서브쿼리는 사라졌지만 전역 목록(S1·S3)에 남은 **풀스캔 + filesort**를 인덱스로 제거한다. 단일 → 복합 → 커버링 순으로 각 인덱스 타입의 효과·한계를 관찰(illustrate)한 뒤 **최종 세트**를 골라 S1~S4 API 성능까지 측정한다.
- **캐시 상태**: off
- **기대**: 정렬 키(`like_count`·`created_at`)와 필터(`brand_id`)를 인덱스가 대신해 filesort·풀스캔이 사라진다. 단일 인덱스로는 정렬·필터를 동시에 못 잡고(3a), 복합 인덱스로 정렬 생략(3b), 커버링으로 북마크 조회까지 제거(3c)됨을 단계적으로 확인한다.

> **측정 방식**: illustrate(3a~3d)는 `EXPLAIN`/`EXPLAIN ANALYZE`만 관찰(단일 쿼리). 측정 DB(`perf`)는 `ddl-auto:none`이라 인덱스를 SQL로 직접 `CREATE`/`DROP` 하며 본다 — 재현 스크립트는 `measurement/sql/03-explain-index.sql`. **API 동시 50명 측정은 최종 인덱스 세트에서 한 번** 수행하고, 최종 세트만 엔티티 `@Table(indexes=…)`에 반영한다.

---

## 3a. 단일 인덱스의 한계 — 정렬 OR 필터 중 하나만 해결한다

> 단일 인덱스를 하나씩 걸고 S1(전역 좋아요순)·S2(브랜드 좋아요순)의 계획 변화를 관찰한다.
> 시작 상태: `products`에 `PRIMARY(id)`만 존재.

### 3a-1. 정렬 인덱스 `(like_count)` 단독

```sql
CREATE INDEX idx_like_count ON products (like_count);
```

#### S1 : 좋아요순 + 전역

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | index | idx_like_count | 199 | Using where; **Backward index scan** |
| 브랜드 b | eq_ref | PRIMARY | 1 | Using where |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (idx_like_count 역방향 스캔) | 20 | 1 | 0.085ms |
| 브랜드 b (PK 단건 lookup) | 1 | 20 | 0.0055ms/회 |

```
-> Index scan on p using idx_like_count (reverse)  (cost=18.3 rows=199) (actual time=0.0819..0.085 rows=20 loops=1)
```

- **단건 총 실측: 0.216ms** (비정규화 단계 62.5ms → 0.216ms)

**왜 추정과 실측이 다른가**: InnoDB 보조 인덱스는 행 위치자로 PK를 덧붙여 물리적으로 `(like_count, id)` 순으로 정렬돼 있다. 이 인덱스를 **역방향(Backward)으로 스캔**하면 `ORDER BY like_count DESC, id DESC`와 정확히 일치 → **filesort가 통째로 사라진다.** 10만 행 풀스캔 대신 인덱스 끝에서 20행만 읽고 멈춘다(`LIMIT 20`). 즉 **전역 정렬 시나리오는 단일 인덱스만으로 거의 끝난다.**

> **추정행 199의 출처**: `199 ≈ LIMIT(20) ÷ 추정 선택도(10%)`. 옵티마이저 트레이스에서 `deleted_at IS NULL` 필터가 `99,433행 → rows_for_plan 9,943.3`(선택도 10%)으로 잡히고, EXPLAIN의 `filtered=10.00`이 이를 노출한다. 인덱스가 정렬을 제공하니 LIMIT 20개를 채우면 멈추는데, 한 행당 통과율 10%면 20개를 모으려 ~200행을 읽는다고 추정 → 199. 단 **10%는 통계 없는 `IS NULL`에 대한 MySQL 기본 추정치**일 뿐이고, 실제론 대부분 미삭제(통과율 ≈100%)라 ANALYZE 실측은 20행에서 끝난다(추정 199 ≫ 실측 20). 추정행은 휴리스틱이라 신뢰 대상이 아니다 — 실측은 ANALYZE로 본다.

#### S2 : 좋아요순 + 인기 브랜드(847) — `(like_count)`만 있을 때

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 브랜드 b | const | PRIMARY | 1 | - |
| 상품 p | index | idx_like_count | 20 | Using where; Backward index scan |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (idx_like_count 역방향 스캔) | **1,916** (필터 통과 20) | 1 | 4.94ms |

```
-> Filter: ((p.brand_id = 847) and (p.deleted_at is null))  (actual time=0.0644..5.05 rows=20 loops=1)
    -> Index scan on p using idx_like_count (reverse)  (cost=2.01 rows=20) (actual time=0.0608..4.94 rows=1916 loops=1)
```

- **단건 총 실측: 5.06ms**

**왜 추정과 실측이 다른가**: 정렬은 인덱스로 해결되지만 `brand_id=847` 필터는 인덱스로 **못 좁힌다.** 그래서 like_count 내림차순으로 스캔하며 brand가 847이 아닌 행을 하나씩 버린다(**scan-and-discard**). 옵티마이저는 20행만 읽는다고 추정했지만 실제로는 **1,916행을 읽고서야** 847에 해당하는 20개를 채웠다. 847이 좋아요 상위 브랜드라 1,916행에서 멈췄을 뿐, **비인기 브랜드였다면 인덱스 전체를 훑어야 해 비용이 폭발**한다.

### 3a-2. 필터 인덱스 `(brand_id)` 단독

```sql
DROP INDEX idx_like_count ON products;
CREATE INDEX idx_brand_id ON products (brand_id);
```

#### S2 : 좋아요순 + 인기 브랜드(847) — `(brand_id)`만 있을 때

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 브랜드 b | const | PRIMARY | 1 | **Using filesort** |
| 상품 p | ref | idx_brand_id | 1,134 | Using where |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (idx_brand_id lookup) | 1,134 | 1 | 2.29ms |
| Sort (like_count DESC, id DESC, top-N) | 20 | 1 | 2.46ms 누적 |

```
-> Sort: p.like_count DESC, p.id DESC, limit input to 20 row(s) per chunk  (actual time=2.46..2.46 rows=20 loops=1)
    -> Index lookup on p using idx_brand_id (brand_id=847)  (cost=295 rows=1134) (actual time=0.208..2.29 rows=1134 loops=1)
```

- **단건 총 실측: 2.46ms**

**왜 추정과 실측이 다른가**: 이번엔 정반대다. `brand_id` 인덱스가 847을 **1,134행으로 정확히 좁히지만**, 그 1,134행을 `like_count`로 정렬할 방법이 없어 **`Using filesort`가 잔존**한다(top-N 힙 정렬). 필터는 인덱스가, 정렬은 메모리가 담당하는 절반의 해결.

#### S1 : 좋아요순 + 전역 — `(brand_id)`만 있을 때 (필터 인덱스가 무용한 경우)

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 브랜드 b | ALL | 없음 | 1,000 | Using where; **Using temporary; Using filesort** |
| 상품 p | ref | idx_brand_id | 97 | Using where |

**왜**: 전역 조회(brand 필터 없음)에선 `(brand_id)` 인덱스가 정렬에도 필터에도 쓸모가 없다. 오히려 옵티마이저가 brands를 ALL 스캔하고 `Using temporary; Using filesort`로 **베이스라인보다 나쁜 계획**을 만든다 — 잘못된 인덱스는 없느니만 못함을 보여준다.

```sql
DROP INDEX idx_brand_id ON products;
```

### 3a 결론

| 단일 인덱스 | 정렬(ORDER BY like_count) | 필터(brand_id) | S2 한계 |
|---|---|---|---|
| `(like_count)` | ✅ 커버 (filesort 제거) | ❌ 못 좁힘 | scan-and-discard (1,916행 읽음) |
| `(brand_id)` | ❌ 못 커버 (filesort 잔존) | ✅ 좁힘 (1,134행) | filesort 잔존 |

> **단일 인덱스는 정렬 OR 필터 중 하나만 해결한다.** 필터와 정렬이 동시에 걸리는 S2는 어느 단일 인덱스로도 둘 다 잡지 못한다 → 두 컬럼을 한 인덱스에 담는 **복합 인덱스 `(brand_id, like_count, id)`** 가 필요(3b). 한편 전역 정렬(S1)은 단일 `(like_count)`만으로 0.216ms까지 떨어져, 복합/커버링 없이도 큰 몫이 해결됨을 함께 확인했다.

---

## 3b. 복합 인덱스 + leftmost prefix — 필터와 정렬을 한 인덱스로

> 단일 인덱스가 못 잡은 "필터+정렬 동시"를 복합 인덱스로 해결하고, **컬럼 순서**가 전부임을 확인한다.

### 3b-1. 복합 `(brand_id, like_count, id)` — S2의 필터+정렬 동시 해결

```sql
CREATE INDEX idx_brand_like_id ON products (brand_id, like_count, id);
```

**나가는 쿼리**: S2 (좋아요순 + 브랜드 847) — 3a와 동일.

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 브랜드 b | const | PRIMARY | 1 | - |
| 상품 p | **ref** | idx_brand_like_id | 1,134 | Using where; **Backward index scan** |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (idx_brand_like_id ref + 역방향) | **20** | 1 | 0.89ms |

```
-> Index lookup on p using idx_brand_like_id (brand_id=847) (reverse)  (cost=295 rows=1134) (actual time=0.887..0.89 rows=20 loops=1)
```

- **단건 총 실측: 0.901ms**

**왜 추정과 실측이 다른가**: 선두 컬럼 `brand_id=847`이 **equality라 `ref` 접근**으로 브랜드 847 구간만 집어 든다. 그 구간 안에서 행은 이미 `(like_count, id)` 순으로 정렬돼 있어 **역방향 스캔이 `ORDER BY like_count DESC, id DESC`와 일치 → filesort 제거.** 게다가 `LIMIT 20`이라 그 구간의 **앞 20행만 읽고 멈춘다**(추정 1,134행이지만 실측 20행). 단일 인덱스가 각각 절반만 하던 필터·정렬을 **하나의 인덱스가 둘 다** 해결한다.

### 3b-2. 컬럼 순서를 뒤집으면 — `(like_count, brand_id)` (leftmost prefix 깨짐)

```sql
CREATE INDEX idx_like_brand ON products (like_count, brand_id);
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 브랜드 b | const | PRIMARY | 1 | - |
| 상품 p | **index** (풀 인덱스 스캔) | idx_like_brand | 20 | Using where; Backward index scan |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (idx_like_brand 역방향 풀스캔) | **1,907** (필터 통과 20) | 1 | 5.49ms |

```
-> Index scan on p using idx_like_brand (reverse)  (cost=2.01 rows=20) (actual time=0.0803..5.49 rows=1907 loops=1)
```

- **단건 총 실측: 5.59ms**

**왜 추정과 실측이 다른가**: 같은 두 컬럼인데 순서만 뒤집었더니 `ref`가 `index`(풀 인덱스 스캔)로 추락했다. `brand_id`가 인덱스에 **있어도 선두가 아니라(`like_count`가 앞)** equality 필터로 못 쓴다 — **leftmost prefix 법칙**. 결국 `like_count` 순서로 인덱스를 훑으며 `brand_id=847`을 하나씩 버리는 scan-and-discard(1,907행)로, 3a의 단일 `(like_count)`(1,916행)와 사실상 동일하게 퇴화한다.

### 3b-3. 최신순 `(created_at, id)` — S3의 filesort 제거

```sql
CREATE INDEX idx_created_id ON products (created_at, id);
```

**나가는 쿼리**: S3 (최신순 + 전역).

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | index | idx_created_id | 199 | Using where; **Backward index scan** |
| 브랜드 b | eq_ref | PRIMARY | 1 | Using where |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (idx_created_id 역방향 스캔) | 20 | 1 | 0.0725ms |
| 브랜드 b (PK 단건 lookup) | 1 | 20 | 0.0047ms/회 |

- **단건 총 실측: 0.176ms** (비정규화 단계 46.8ms → 0.176ms)

**왜 추정과 실측이 다른가**: S1의 `(like_count)`와 같은 구조다 — `(created_at, id)` 역방향 스캔이 `ORDER BY created_at DESC, id DESC`와 정확히 일치해 filesort가 사라진다. 추정행 199는 S1과 똑같이 `deleted_at IS NULL`의 10% 기본 추정에서 온 것이고, 실측은 20행에서 끝난다.

### 3b 결론 — S2 한 시나리오로 본 인덱스 형태별 차이

| S2 인덱스 | 접근방식 | filesort | 읽은 행(실측) | 단건 실측 |
|---|---|---|---|---|
| `(like_count)` 단독 | index scan | 없음 | 1,916 | 5.06ms |
| `(brand_id)` 단독 | ref | **잔존** | 1,134 | 2.46ms |
| **`(brand_id, like_count, id)`** | **ref + 역방향** | **없음** | **20** | **0.90ms** |
| `(like_count, brand_id)` (순서 뒤집음) | index scan | 없음 | 1,907 | 5.59ms |

> **복합 인덱스는 필터+정렬을 한 번에** 해결한다 — `(brand_id, like_count, id)`가 20행만 읽고 0.90ms로 가장 빠르다. 단 **컬럼 순서가 전부**다: equality 필터 컬럼(`brand_id`)을 선두에 둬야 `ref`로 구간을 좁히고, 그 뒤에 정렬 컬럼을 둬야 filesort가 사라진다. 순서를 뒤집으면 같은 컬럼으로도 단일 인덱스만큼 퇴화한다. 다음(3c)은 이 위에서 테이블 본체 조회(북마크 lookup)까지 없애는 커버링 인덱스.
>
> **타이브레이크 `id`는 명시 불필요**: 위 실험은 `(brand_id, like_count, id)`로 끝에 `id`를 적었지만, InnoDB는 보조 인덱스 리프에 **PK(`id`)를 자동 부착**한다. 그래서 `(brand_id, like_count)`만 만들어도 물리적으로 동일하게 저장돼 `ORDER BY like_count DESC, id DESC`의 `id DESC`까지 역방향 스캔으로 충족된다(EXPLAIN의 `key_len`도 동일). **최종 세트는 `id`를 생략**한 `(brand_id, like_count)`·`(like_count)`로 둔다.

## 3c. 커버링 인덱스 — 북마크 lookup 제거 (`Using index`)

> 정렬 인덱스만으로는 인덱스에서 `(like_count, id)`만 얻고, 나머지 SELECT 컬럼(name·price·stock·brand_id)은 **테이블 본체로 되돌아가 읽는다(북마크 lookup)**. SELECT/WHERE 컬럼을 전부 인덱스에 넣으면 그 되돌아가기가 사라진다 — S1으로 확인.

### 3c-1. 비커버링 `(like_count)` — 북마크 lookup 발생

```sql
CREATE INDEX idx_like_count ON products (like_count);
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | index | idx_like_count | 199 | Using where; Backward index scan |
| 브랜드 b | eq_ref | PRIMARY | 1 | Using where |

> Extra에 **`Using index`가 없다** = 인덱스만으로 SELECT를 못 채워 테이블 본체를 조회한다.

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (idx_like_count 역방향 + 북마크 lookup) | 20 | 1 | 0.856ms |
| 브랜드 b (PK 단건 lookup) | 1 | 20 | 0.0047ms/회 |

```
-> Index scan on p using idx_like_count (reverse)  (actual time=0.852..0.856 rows=20 loops=1)
```

- **단건 총 실측: 0.969ms**

### 3c-2. 커버링 `(like_count, id, brand_id, name, price, stock, deleted_at)`

```sql
CREATE INDEX idx_cover_s1 ON products (like_count, id, brand_id, name, price, stock, deleted_at);
```

정렬 키 `(like_count, id)`를 선두에 두고, S1이 products에서 읽는 나머지 컬럼(`brand_id, name, price, stock`)과 WHERE 컬럼(`deleted_at`)을 전부 포함시킨다.

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | index | idx_cover_s1 (key_len **439**) | 20 | Using where; Backward index scan; **Using index** |
| 브랜드 b | eq_ref | PRIMARY | 1 | Using where |

> Extra에 **`Using index`** 등장 = 테이블 본체를 안 건드리고 인덱스만으로 끝낸다.

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (**Covering index scan** 역방향) | 20 | 1 | 0.0135ms |
| 브랜드 b (PK 단건 lookup) | 1 | 20 | 0.001ms/회 |

```
-> Covering index scan on p using idx_cover_s1 (reverse)  (actual time=0.009..0.0135 rows=20 loops=1)
```

- **단건 총 실측: 0.0423ms**

**왜 추정과 실측이 다른가**: products 노드가 **0.856ms → 0.0135ms**로 떨어졌다. 차이는 정확히 **20번의 북마크 lookup**(클러스터드 인덱스로 되돌아가 name·price·stock·brand_id를 읽는 랜덤 접근)이다. 커버링 인덱스는 그 컬럼들을 인덱스 리프에 함께 들고 있어 되돌아갈 필요가 없다(`Covering index scan`).

### 3c 결론 — 효과와 트레이드오프

| S1 인덱스 | Extra | products 노드 실측 | 인덱스 key_len |
|---|---|---|---|
| `(like_count)` 비커버링 | Backward index scan | 0.856ms (북마크 lookup 포함) | 4 |
| 커버링(7컬럼) | … ; **Using index** | **0.0135ms** | **439** |

> 커버링 인덱스는 북마크 lookup을 없애 읽기를 빠르게 한다. 단 대가가 분명하다 — `key_len 439`(`name varchar(100)` 등 포함)로 **인덱스가 비대**해져 **쓰기·저장 비용**이 커진다. 게다가 `LIMIT 20`에선 절약하는 게 **20번의 lookup**뿐이라 단건 절대 시간은 작다(0.9ms→0.04ms, 변동 폭 안). 또 brands 조인은 여전히 PK lookup이라 **쿼리 전체가 index-only는 아니다.** 따라서 커버링의 진짜 가치는 **동시성에서 랜덤 IO 경합을 줄이는 것** — 최종 인덱스 세트에 넣을지는 그 효과를 k6로 확인한 뒤 마무리에서 판단한다(LIMIT 20 + 넓은 컬럼이라 과한 인덱스일 수 있음).

## 3d. 옵티마이저·통계·인덱스 무력화 — 인덱스는 "있어도 안 쓰는" 게 옳을 때가 있다

### 3d-1. `ANALYZE TABLE` — 통계 갱신 효과

`idx_brand_id`를 건 뒤 `ANALYZE TABLE products` 전후의 인덱스 카디널리티와 S2 추정행을 비교했다.

| | 카디널리티(`idx_brand_id`) | S2(`brand_id=847`) 추정행 |
|---|---|---|
| ANALYZE 전 | 970 | 1,134 |
| ANALYZE 후 | 1,102 | 1,134 |
| (실제) | distinct brand_id = **1,000** | 실제 brand 847 행수 ≈ 1,134 |

**해석**: InnoDB 인덱스 카디널리티는 **랜덤 다이브 샘플링**(`innodb_stats_sample_pages`)으로 추정해서, `ANALYZE`마다 참값(1,000) 근처에서 흔들린다(970↔1,102) — 여기선 오히려 갱신 전이 더 가까웠다. 한편 `brand_id=847` equality의 추정행 1,134는 **그 값에 대한 개별 index dive**로 구하므로 글로벌 카디널리티와 무관하게 이미 정확해, ANALYZE 전후로 **플랜이 바뀌지 않는다.** → `ANALYZE`의 진짜 가치는 통계가 망가졌을 때(**대량 적재 직후**, auto-recalc off)지, 정상 상태에선 대개 불필요하다.

### 3d-2. 인덱스 무력화 — 비선택적 조건이면 Full Scan이 더 싸다

`idx_brand_id`가 있는 상태에서 **매칭 행이 많은** 조건(`brand_id BETWEEN 1 AND 500`, 약 50% = 50,155행)을 조회했다.

**EXPLAIN — 자연 선택 (옵티마이저 판단)**

| 테이블 | 접근방식 | possible_keys | 실제 사용 key | Extra |
|---|---|---|---|---|
| 상품 p | **ALL (풀스캔)** | idx_brand_id | **NULL** | Using where |

> 인덱스를 **보고도(`possible_keys`) 안 쓴다(`key=NULL`).**

**EXPLAIN ANALYZE — 자연 선택 vs `FORCE INDEX` 강제**

| 실행 | 접근방식 | 실측시간 |
|---|---|---|
| 자연 선택 | Table scan (full scan) | **23.7ms** |
| `FORCE INDEX (idx_brand_id)` | Index range scan | **47.7ms** (약 2배 느림) |

```
자연:   -> Table scan on p  (actual time=0.0468..18.4 rows=100000)  → filter rows=50155, 23.7ms
강제:   -> Index range scan on p using idx_brand_id (1<=brand_id<=500)  (actual ... rows=50155, 47.7ms)
```

**해석**: 매칭 행이 50,155개(절반)면 인덱스 경로는 보조 인덱스에서 5만 건을 읽고 **건건이 테이블 본체로 북마크 lookup(랜덤 IO)** 해야 한다 — 순차적으로 쭉 읽는 풀스캔보다 오히려 비싸다(47.7ms vs 23.7ms). 그래서 옵티마이저가 인덱스를 **버린 게 옳은 비용 판단**이다. **인덱스는 선택적(소수 매칭)일 때만 이득**이고, 넓게 매칭하는 조건엔 독이 된다 — 3a~3c의 인덱스가 효과적이었던 건 S1·S2가 `LIMIT 20`으로 소수만 집었기 때문이다.

### 3d 결론

> 통계(카디널리티)는 **샘플링 추정**이고, 옵티마이저는 그 추정으로 **비용을 비교해** 인덱스를 쓸지 풀스캔할지 고른다. 매칭이 많으면 풀스캔이 옳다. 인덱스를 "거는 것"만큼 "언제 안 쓰이는지"를 아는 게 중요하다.

---

## 3e. 조인 함정과 해결 — 인덱스가 아니라 쿼리가 범인이었다

> **배경**: 3a~3d로 최종 인덱스 6개를 다 걸자, illustrate에선 빨랐던 **전역 목록(S1·S3)이 오히려 p95 2.8s로 폭증**했다. EXPLAIN을 떠 보니 옵티마이저가 정렬 인덱스를 버리고 **brands를 풀스캔 + 10만 행 filesort**(174ms)하고 있었다. `brand_id` 선두 복합 인덱스 `(brand_id, like_count)`가 생기자 옵티마이저가 **"`b.deleted_at IS NULL`로 삭제 브랜드를 먼저 걸러내면 싸겠다"**(삭제 10% 과소 추정)고 오판해 brands를 driving으로 잡았고, 그러면 정렬 키 `like_count` 인덱스를 선두에서 못 타 filesort가 강제됐다. 정렬 컬럼과 무관하게 **전역 정렬 전부**에 나타난다.
>
> 그런데 이 `b.deleted_at IS NULL`은 **중복 필터**다. 우리 도메인은 브랜드 삭제 시 같은 트랜잭션에서 상품도 함께 삭제하므로(`BrandFacade.deleteBrand` — `brand.delete()` 후 `findActiveByBrandId(brandId).forEach(delete)`), `p.deleted_at IS NULL`이 `b.deleted_at IS NULL`을 **이미 함의**한다(삭제 브랜드의 상품은 반드시 `p.deleted_at IS NOT NULL`). 한 행도 안 바꾸면서 오판만 부른 필터다. 또 목록은 `Page`라 **count 쿼리**가 함께 나가는데, `COUNT(p.id)`는 brands를 쓰지도 않으면서 JOIN해 10만 행 nested loop(77.6ms)를 돌았다.
>
> **해법은 인덱스가 아니라 쿼리 두 줄**이다 — ① content: `b.deleted_at IS NULL` 필터 제거(JOIN은 `b.name` 위해 유지), ② count: `JOIN brands` 통째 제거. 6개 인덱스는 그대로 둔다. 아래는 그 **해결형 쿼리**의 실행계획이다(제거 전 함정 plan은 `measurement/sql/03-explain-index.sql` 주석 참고).
> 시작 상태: `products`에 최종 인덱스 6개 적용(`idx_products_like_count`, `…_brand_id_like_count`, `…_created_at`, `…_price`, `…_brand_id_created_at`, `…_brand_id_price`). 정렬 타이브레이크 `id`는 InnoDB가 PK를 자동 부착하므로 명시하지 않는다(3b 참고).

### 3e-1. content — S1 좋아요순 + 전역 (`b.deleted_at IS NULL` 제거)

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | index | idx_products_like_count | 20 | Using where; **Backward index scan** |
| 브랜드 b | **eq_ref** | PRIMARY | 1 | - |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (idx_products_like_count 역방향 스캔) | 20 | 1 | 0.057ms |
| 브랜드 b (PK 단건 lookup) | 1 | 20 | 0.004ms/회 |

```
-> Limit: 20 row(s)  (actual time=0.0699..0.151 rows=20 loops=1)
    -> Nested loop inner join  (actual time=0.0696..0.15 rows=20 loops=1)
        -> Filter (p.deleted_at is null) → Index scan on p using idx_products_like_count (reverse)  (rows=20 loops=1)
        -> Single-row index lookup on b using PRIMARY (id=p.brand_id)  (rows=1 loops=20)
```

- **단건 총 실측: 0.15ms** (필터 남겼을 때 brands-first 풀스캔 174ms → 0.15ms, 10만 행 → 20행)

**왜 해결되나**: `b.deleted_at IS NULL`이 사라지자 brands는 **필터 효과 0%인 1:1 PK 매칭**이 됐고(FK라 모든 상품은 브랜드를 가짐), 옵티마이저는 brands-first의 비용 이득이 없어져 **products를 driving**으로 되돌렸다. products는 `idx_products_like_count`를 역방향으로 타 **20행에서 멈추고**, brands는 그 20개의 `brand_id`로 `eq_ref` PK lookup만 한다.

### 3e-2. count — 전역 총 개수 (`JOIN brands` 제거)

**나가는 쿼리**
```sql
SELECT COUNT(p.id) FROM products p
WHERE p.deleted_at IS NULL;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | **ALL (풀스캔)** | (없음) | 99,533 | Using where |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 단계 | 실측행 | 실측시간 |
|---|---|---|
| products 풀스캔 → filter deleted_at | 100,000 | 18.3ms |
| Aggregate count(p.id) | 1 | **21.3ms 누적** |

```
-> Aggregate: count(p.id)  (actual time=21.3..21.3 rows=1 loops=1)
    -> Filter: (p.deleted_at is null)  (actual time=0.248..18.3 rows=100000 loops=1)
        -> Table scan on p  (actual time=0.247..13.9 rows=100000 loops=1)
```

- **단건 총 실측: 21.3ms** (JOIN 남겼을 때 10만 nested loop 77.6ms → 21.3ms)

**왜 해결되나**: `COUNT(p.id)`는 `b.name`도 안 쓰므로 brands JOIN이 무용하다. 떼면 products 단독 풀스캔 COUNT로 줄어든다. 단 남는 **21ms는 전역 10만 행 풀스캔 자체** — `deleted_at`은 저선택도라 인덱스로 못 줄인다(3d). 이 비용은 캐시 단계(04)의 동기가 된다.

### 3e-3. content — S3 최신순 + 전역 (`b.deleted_at IS NULL` 제거)

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | index | idx_products_created_at | 20 | Using where; **Backward index scan** |
| 브랜드 b | **eq_ref** | PRIMARY | 1 | - |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (idx_products_created_at 역방향 스캔) | 20 | 1 | 0.077ms |
| 브랜드 b (PK 단건 lookup) | 1 | 20 | 0.0017ms/회 |

```
-> Limit: 20 row(s)  (actual time=0.0732..0.116 rows=20 loops=1)
    -> Nested loop inner join  (actual time=0.0728..0.115 rows=20 loops=1)
        -> Filter (p.deleted_at is null) → Index scan on p using idx_products_created_at (reverse)  (rows=20 loops=1)
        -> Single-row index lookup on b using PRIMARY (id=p.brand_id)  (rows=1 loops=20)
```

- **단건 총 실측: 0.12ms**

**왜 해결되나**: S1과 같은 구조다 — 전역 최신순도 같은 함정이었고, 필터 제거로 `idx_products_created_at` 역방향 스캔이 살아나 20행에서 멈춘다.

### 3e-4. S2 브랜드847 — 애초에 함정 없음 (참고)

> `brand_id=847`이 들어오면 brands는 `const` 조인이 되고 products는 복합 `(brand_id, like_count)`를 `ref`로 타므로, 함정(전역에서만 발생)과 무관하다. 해결형으로도 동일하게 잘 탄다.

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 쿼리 | 접근 | 실측행 | 실측시간 |
|---|---|---|---|
| content | products `idx_products_brand_id_like_count` ref 역방향 | 20 | 0.63ms |
| count | 같은 복합 ref | 1,134 | 1.4ms |

### 3e 결론

> **조인 쿼리에서 인덱스는 한 테이블에서만 탄다** — 나머지 테이블은 조인 키로 따라올 뿐이다. 그래서 brand 복합 인덱스가 전역 정렬에서 옵티마이저를 brands-first로 오판하게 만들었지만, **근본 원인은 인덱스가 아니라 쿼리**였다: content의 `b.deleted_at IS NULL` 중복 필터(174ms→0.15ms)와 count의 불필요한 brands JOIN(77.6ms→21.3ms). 두 줄을 고쳐 **6개 인덱스를 그대로 둔 채** 전역·브랜드 정렬이 모두 산다.
>
> **트레이드오프**: 이 쿼리는 이제 "브랜드 삭제 = 상품 삭제"라는 **도메인 불변식에 암묵적으로 의존**한다(`BrandFacade.deleteBrand`가 같은 트랜잭션으로 보장). 만약 이 동기화가 깨지거나 이벤트로 분리되면 삭제된 브랜드의 상품이 노출될 수 있다 — 그 경우엔 애초에 두 테이블을 조인할 수 있는지부터 다시 봐야 한다. 최종 코드는 content 4쿼리에서 필터를, count 3쿼리에서 JOIN을 제거했다.

---

## 최종 인덱스 세트 — S1~S4 API 성능 (동시 50명)

### 채택한 인덱스 6개 (엔티티 `@Table(indexes=…)` + perf DB 동일)

| 인덱스 | 컬럼 | 담당 정렬·필터 |
|---|---|---|
| `idx_products_like_count` | (like_count) | S1 전역 좋아요순 |
| `idx_products_brand_id_like_count` | (brand_id, like_count) | S2 브랜드 좋아요순 |
| `idx_products_created_at` | (created_at) | S3 전역 최신순 |
| `idx_products_price` | (price) | 전역 가격순 |
| `idx_products_brand_id_created_at` | (brand_id, created_at) | 브랜드 최신순 |
| `idx_products_brand_id_price` | (brand_id, price) | 브랜드 가격순 |

> **커버링 인덱스는 제외**했다(3c: LIMIT 20이라 절약 효과 작고 key_len 439 쓰기 비용). `deleted_at`도 제외(3d/멘토: 대부분 NULL인 저선택도 컬럼은 인덱스 무의미).

### 함께 바꾼 쿼리 두 가지 (3e의 결론)

| 쿼리 | 변경 | 이유 |
|---|---|---|
| content(목록 20건) | `JOIN brands` 유지, **`b.deleted_at IS NULL` 필터만 제거** | 조인 함정 트리거 제거. b.name은 계속 JOIN으로 가져옴 |
| count(총 개수) | **`JOIN brands` 통째 제거** | `COUNT(p.id)`는 brands를 안 씀(b.name도 SELECT 안 함) |

### 시나리오별 측정 (최종 세트)

> 목록 API는 content(정렬+LIMIT 20)와 count(총 개수) **두 쿼리**가 함께 나간다. 단건 EXPLAIN ANALYZE는 둘을 분리해 본다.

| 시나리오 | content 접근(실측) | count 접근(실측) | API p50 / **p95** / max | rps | 에러 |
|---|---|---|---|---|---|
| **S1** 좋아요·전역 | `idx_products_like_count` 역방향, 20행 (0.15ms) | products 풀스캔 COUNT, 10만 행 (**~21ms**) | 155 / **260** / 529 ms | 304 | 0% |
| **S2** 좋아요·브랜드847 | `idx_products_brand_id_like_count` ref 역방향, 20행 (0.63ms) | 같은 복합 ref, 1,134행 (1.4ms) | 38 / **69** / 226 ms | 1,206 | 0% |
| **S3** 최신·전역 | `idx_products_created_at` 역방향, 20행 (0.12ms) | products 풀스캔 COUNT, 10만 행 (~21ms) | 150 / **247** / 685 ms | 313 | 0% |
| **S4** 상세 단건 | PK + brands PK lookup (≈0.1ms) | (count 없음) | 17 / **23** / 92 ms | 2,896 | 0% |

**나가는 쿼리 (S1 최종형)**
```sql
-- content
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id
WHERE p.deleted_at IS NULL AND (:brandId IS NULL OR p.brand_id = :brandId)
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
-- count (brands JOIN 없음)
SELECT COUNT(p.id) FROM products p
WHERE p.deleted_at IS NULL AND (:brandId IS NULL OR p.brand_id = :brandId);
```

## 이 단계 총괄 — 비정규화(02) 대비

| 시나리오 | ② 비정규화 p95 | ③ 인덱스 p95 | 개선 | 무엇이 사라졌나 |
|---|---|---|---|---|
| S1 좋아요·전역 | 2,790ms | **260ms** | **10.7×** | content filesort(조인 함정) + count 불필요 JOIN |
| S2 좋아요·브랜드 | 560ms | **69ms** | **8.1×** | content filesort (복합 인덱스 ref) |
| S3 최신·전역 | 2,860ms | **247ms** | **11.6×** | content filesort + count 불필요 JOIN |
| S4 상세 단건 | 19ms | 23ms | ≈동일 | (인덱스 영향 없음, 오차 범위) |

## 직전 단계 대비 & 해석

비정규화(02)로 좋아요 서브쿼리는 사라졌지만 전역 목록은 여전히 p95 2.8s였다. Stage 3에서 두 겹의 병목을 걷어냈다:

1. **content 쿼리 — 조인 함정(3e)**: brand 복합 인덱스가 옵티마이저를 brands-first로 오판하게 만들어 10만 행 filesort가 발생했다. 인덱스가 아니라 **`b.deleted_at IS NULL` 중복 필터**가 원인이었고(도메인상 `p.deleted_at IS NULL`이 이미 함의), 필터 한 줄 제거로 정렬 인덱스가 살아나 0.15ms가 됐다.
2. **count 쿼리 — 불필요한 JOIN**: `Page` 반환에 딸려나가는 `COUNT(p.id)`가 쓰지도 않는 brands를 조인해 10만 행 nested loop(77ms)를 돌았다. JOIN을 떼니 21ms로 줄었고, 전역 목록 단건이 81→35ms, 동시 50명 p95가 2.6s→260ms로 떨어졌다.

**남은 한계 → Stage 4(캐시) 동기**: 전역 count는 여전히 **10만 행 풀스캔(~21ms)**이다. `deleted_at`은 저선택도라 인덱스로 줄일 수 없고(3d), 이 21ms가 동시 50명에서 직렬 병목으로 p95를 지배한다. 이는 인덱스가 아니라 **자주·동일하게 반복되는 전역 인기 목록을 캐시로 DB에서 덜어내야** 할 문제다(핫셋 캐시 + 짧은 TTL). 정렬·필터 비용을 인덱스로 끝낸 지금, 다음 병목은 "DB까지 가는 횟수"다.
