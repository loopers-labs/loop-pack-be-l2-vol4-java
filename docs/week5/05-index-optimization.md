# 05. 인덱스 최적화 — 브랜드 필터 + 좋아요순 상품 목록

## 0. 이 문서의 역할

상품 목록 조회(브랜드 필터 + 좋아요순 정렬)에 대해 **대용량 데이터(상품 12만건)** 환경에서
`EXPLAIN` 으로 실행 계획을 분석하고, 복합 인덱스를 설계·적용해 **개선 전후 성능을 비교**한다.

재현 자산은 모두 저장소에 포함된다:
- 벤치마크 SQL — [`../../benchmark/sql/`](../../benchmark/sql/) (`01_schema` → `05_explain_after`)
- 러너 — [`../../benchmark/run.sh`](../../benchmark/run.sh)
- 실측 원본 — [`../../benchmark/results/`](../../benchmark/results/)
- 운영 반영 — `ProductEntity.@Table(indexes=...)`

---

## 0.1 측정 환경 / 도구 / 시나리오

이 문서의 수치는 **부하 테스트가 아니라 단일 세션에서 `EXPLAIN ANALYZE` 로 단건 쿼리를 측정**한 값이다.
따라서 RPS·동시 사용자 같은 처리량 차원은 측정 대상이 아니다(동시성 부하는 별도 과제).

**환경 (CPU/메모리/컨테이너 구성)**

- 호스트: AMD Ryzen 5 4500U (6코어/6스레드), RAM 16GB, Windows 10 Pro
- 컨테이너 런타임: Rancher Desktop (WSL2 백엔드, VM 2 vCPU·4GB 할당)
- DB: MySQL 8.0.46 (`mysql:8.0` 단일 컨테이너, 리소스 한도 미설정 → VM 자원 공유)
- 측정 대상 데이터: `loopers_bench` 스키마

**부하 도구**

- 해당 없음 — `mysql` CLI 로 `EXPLAIN` / `EXPLAIN ANALYZE` 를 단건 실행(`benchmark/run.sh`, 단일 세션).
  ASC↔DESC 비교는 3회 반복 후 typical 값을 기록(§8.2).

**시나리오 (RPS · 동시 사용자 수 · 데이터 규모)**

- RPS: 해당 없음 (단건 쿼리 측정)
- 동시 사용자 수: 해당 없음 (단일 세션·단일 쿼리)
- 데이터 규모: 상품 **120,000건**(활성 110,638건), 브랜드 50개, `likes_count` 0~49,998 멱급수 편중, soft-delete ~7.8%
- 측정 쿼리: A) `brand_id=? AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC` / B) 브랜드 조건 제거 — 각 page 0(OFFSET 0) + 딥오프셋(OFFSET 1000)

---

## 1. 대상 쿼리

운영 코드 `ProductRepositoryImpl.findActivePage()` 가 생성하는 목록 쿼리 두 가지다.
정렬은 `likes_count DESC` 에 `id DESC` tie-break(페이지 경계 안정성)을 더한다.

| 구분 | 조건 | 정렬 | 메서드 |
|---|---|---|---|
| **A** | `brand_id = ? AND deleted_at IS NULL` | `likes_count DESC, id DESC` | `findByBrandIdAndDeletedAtIsNull(brandId, pageable)` |
| **B** | `deleted_at IS NULL` | `likes_count DESC, id DESC` | `findByDeletedAtIsNull(pageable)` |

```sql
-- A) 브랜드 필터 + 좋아요순
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY likes_count DESC, id DESC
LIMIT 20 OFFSET 0;
```

---

## 2. 데이터셋

[`02_seed.sql`](../../benchmark/sql/02_seed.sql) — 집합 기반 INSERT(숫자 테이블 cross join)로 일괄 생성.

| 컬럼 | 분포 | 의도 |
|---|---|---|
| `brand_id` | 1~50, `POW(RAND(),2)` 편중 | 인기 브랜드에 상품 집중 (브랜드별 카디널리티 격차) |
| `likes_count` | 0~50,000, `POW(RAND(),3)` 멱급수 편중 | 대부분 0 근처 + 극소수 폭발 → 정렬 의미 + 동점 다수 |
| `price` | 1,000~100,900 (100원 단위) | 가격 다양성 |
| `deleted_at` | 약 8% soft-delete | `deleted_at IS NULL` 필터가 실제로 행을 거름 |
| `created_at` | 최근 730일 분산 | 시계열 다양성 |

측정된 실제 분포(`02_seed_summary.txt`):

- 상품 총건수 **120,000** / 활성(`deleted_at IS NULL`) **110,638** / soft-delete 약 7.8%
- 브랜드 50개 — 최다 브랜드 `brand_id=1` 이 활성 **15,521**건(편중 확인), 2위 6,460건
- `likes_count` 최대 49,998, `likes_count=0` 비율 약 3% (멱급수 편중)

---

## 3. 개선 전 — EXPLAIN 분석

> 보조 인덱스 없음(PK `id` 만). 원본: [`results/03_explain_before.txt`](../../benchmark/results/03_explain_before.txt)

### 3.1 쿼리 A (브랜드 필터 + 좋아요순)

```
-> Limit: 20 row(s)  (cost=12000 rows=20) (actual time=213..213 rows=20 loops=1)
    -> Sort: product.likes_count DESC, product.id DESC, limit input to 20 row(s) per chunk
                                       (cost=12000 rows=120000) (actual time=213..213 rows=20 loops=1)
        -> Filter: ((product.brand_id = 1) and (product.deleted_at is null))
                                       (cost=12000 rows=120000) (actual time=0.121..202 rows=15521 loops=1)
            -> Table scan on product   (cost=12000 rows=120000) (actual time=0.111..182 rows=120000 loops=1)
```

- 스캔 타입: **`Table scan` — 120,000행 전부 읽음**
- 정렬: **`Sort` (filesort)**
- 실제 실행 시간: **약 213 ms**

**문제**: 인덱스가 없어 12만 행을 전부 읽어 `brand_id=1 AND deleted_at IS NULL` 필터를 행마다
평가하고(15,521행 통과), 정렬 가능한 인덱스가 없어 그 결과를 filesort 한다 → LIMIT 20 한 페이지를
위해 사실상 테이블 전체를 훑고 정렬한다.

### 3.2 쿼리 B (전체 + 좋아요순)

```
-> Limit: 20 row(s)  (cost=12000 rows=20) (actual time=328..328 rows=20 loops=1)
    -> Sort: product.likes_count DESC, product.id DESC, limit input to 20 row(s) per chunk
                                       (cost=12000 rows=120000) (actual time=328..328 rows=20 loops=1)
        -> Filter: (product.deleted_at is null)
                                       (cost=12000 rows=120000) (actual time=0.372..272 rows=110638 loops=1)
            -> Table scan on product   (cost=12000 rows=120000) (actual time=0.368..248 rows=120000 loops=1)
```

- 동일하게 풀스캔(120,000행) + filesort(110,638행 정렬). 실제 실행 시간 **약 328 ms**
  (브랜드로 거르지 않아 정렬 대상이 더 커 A보다 느림).

---

## 4. 인덱스 설계

[`04_indexes.sql`](../../benchmark/sql/04_indexes.sql)

```sql
CREATE INDEX idx_brand_active_likes ON product (brand_id, deleted_at, likes_count, id);
CREATE INDEX idx_active_likes       ON product (deleted_at, likes_count, id);
```

**원칙: `[등치 조건] → [정렬 컬럼] → [tie-break]` 순서.**
앞쪽 등치 조건(`brand_id`, `deleted_at IS NULL`)으로 범위를 한 점으로 좁히면, 뒤따르는
`(likes_count, id)` 가 인덱스 물리 순서 그대로 정렬을 충족한다 → **풀스캔·filesort 동시 제거**.

- `idx_brand_active_likes` — 쿼리 A 전용. 선행 컬럼 `brand_id` 등치 → `deleted_at IS NULL` 등치(NULL ref)
  → `likes_count, id` 로 정렬 충족.
- `idx_active_likes` — 쿼리 B 전용. A 인덱스는 선행이 `brand_id` 라 brand 조건이 없는 B 에는
  쓸 수 없으므로 별도로 둔다.

> **이 §5 측정은 ASC 인덱스 기준이다.** MySQL 8 은 `ORDER BY likes_count DESC, id DESC` 를 ASC
> 인덱스의 **backward index scan** 으로도 filesort 없이 처리한다. 다만 backward scan 은 딥 페이지에서
> 느리고, mixed-direction 정렬은 아예 filesort 를 피하지 못한다 → §8 에서 DESC(내림차순) 인덱스와
> 비교하고, **최종적으로 DESC 인덱스를 채택**한다.

**운영 반영 (채택: DESC 인덱스).** JPA `@Index` 는 컬럼 방향을 표현하지 못하므로 DDL 로 정의한다:
- local/test (`ddl-auto: create`) — [`apps/commerce-api/src/main/resources/import.sql`](../../apps/commerce-api/src/main/resources/import.sql) 가 스키마 생성 직후 실행 (통합테스트로 인덱스 생성 검증 완료)
- prd (`ddl-auto: none`) — [`migration_product_indexes.sql`](./migration_product_indexes.sql) 를 운영 DDL 로 적용

---

## 5. 개선 후 — EXPLAIN 분석

> 원본: [`results/05_explain_after.txt`](../../benchmark/results/05_explain_after.txt)

### 5.1 쿼리 A

```
-> Limit: 20 row(s)  (cost=4402 rows=20) (actual time=0.073..0.12 rows=20 loops=1)
    -> Filter: (product.deleted_at is null)  (cost=4402 rows=30306) (actual time=0.0721..0.118 rows=20 loops=1)
        -> Index lookup on product using idx_brand_active_likes (brand_id=1, deleted_at=NULL) (reverse)
                                       (cost=4402 rows=30306) (actual time=0.0714..0.116 rows=20 loops=1)
```

- 스캔 타입: **`Index lookup` on `idx_brand_active_likes`** (등치 `brand_id=1, deleted_at=NULL`)
- 정렬: **filesort 제거** — `(reverse)` = backward index scan 으로 `likes_count DESC, id DESC` 충족
- 읽은 행: **20행만** (LIMIT 만큼) → 실제 실행 시간 **약 0.12 ms**

### 5.2 쿼리 B

```
-> Limit: 20 row(s)  (cost=7291 rows=20) (actual time=0.11..0.16 rows=20 loops=1)
    -> Filter: (product.deleted_at is null)  (cost=7291 rows=59202) (actual time=0.11..0.158 rows=20 loops=1)
        -> Index lookup on product using idx_active_likes (deleted_at=NULL) (reverse)
                                       (cost=7291 rows=59202) (actual time=0.108..0.156 rows=20 loops=1)
```

- `idx_active_likes` 로 backward index scan, filesort 제거, 20행만 읽음 → **약 0.16 ms**

---

## 6. 개선 전후 비교

| 쿼리 | 지표 | 개선 전 | 개선 후 | 개선 |
|---|---|---:|---:|---:|
| A (page 0) | 실행 시간 | 213 ms | 0.12 ms | **약 1,775×** |
| A (page 0) | 읽은 행 | 120,000 (full scan) | 20 (index) | — |
| A (page 0) | filesort | 있음 | 없음 | 제거 |
| B (page 0) | 실행 시간 | 328 ms | 0.16 ms | **약 2,050×** |
| B (page 0) | 읽은 행 | 120,000 (full scan) | 20 (index) | — |
| A (offset 1000) | 실행 시간 | 219 ms | 3.83 ms | 약 57× |
| A (offset 1000) | 읽은 행 | 120,000 (full scan) | 1,020 (index) | — |

데이터: 총 120,000건 / 활성 110,638건 / 최대 likes_count 49,998 / `brand_id=1` 활성 15,521건.

---

## 7. 결론 및 한계

- 등치+정렬을 한 인덱스로 묶어 **풀스캔·filesort 를 제거**, page 0 조회를 1,000배 이상(A 1,775×·B 2,050×) 단축.
- **딥 오프셋 한계**: `LIMIT 20 OFFSET 1000` 은 인덱스를 타도 앞 1,020행을 스캔·폐기하므로
  page 0(0.12 ms) 대비 32배 느린 3.83 ms 다(그래도 풀스캔 219 ms 보다는 57배 빠름).
  근본 해결은 오프셋 대신 **커서(keyset) 페이지네이션**
  (`WHERE (likes_count, id) < (?, ?)`) — 후속 개선 항목으로 둔다.
- 쓰기 비용 트레이드오프: 인덱스 2개 추가로 INSERT/UPDATE(특히 `likes_count` 증감) 시
  인덱스 유지 비용이 늘지만, 읽기 빈도가 압도적인 목록 조회 특성상 이득이 크다.

## 8. 부록 — ASC 인덱스(backward scan) vs DESC 인덱스(forward scan)

§4의 인덱스는 ASC 컬럼이고, MySQL 은 `ORDER BY likes_count DESC, id DESC` 를 **backward index
scan(`reverse`)** 으로 충족했다. 그렇다면 정렬 컬럼을 **명시적 DESC** 로 선언한 인덱스로 바꾸면
(= forward scan) 차이가 있을까? 데이터(12만건)는 그대로 두고 인덱스만 교체해 비교했다.

- 스크립트: [`06_indexes_desc.sql`](../../benchmark/sql/06_indexes_desc.sql)(교체) · [`07_explain_desc.sql`](../../benchmark/sql/07_explain_desc.sql)(측정)
- 원본: [`results/05b_asc_x3.txt`](../../benchmark/results/05b_asc_x3.txt)(ASC) · [`results/07_explain_desc.txt`](../../benchmark/results/07_explain_desc.txt)(DESC)

```sql
CREATE INDEX idx_brand_active_likes_desc ON product (brand_id, deleted_at, likes_count DESC, id DESC);
CREATE INDEX idx_active_likes_desc       ON product (deleted_at, likes_count DESC, id DESC);
```

### 8.1 실행 계획 차이

| | ASC 인덱스 | DESC 인덱스 |
|---|---|---|
| 스캔 방향 | `Index lookup ... (reverse)` — **backward** | `Index lookup ...` — **forward** (`reverse` 없음) |
| `deleted_at IS NULL` | 인덱스 위 **별도 Filter 노드** | **index condition(ICP)** 로 push-down |
| filesort | 없음 | 없음 |

### 8.2 타이밍 (EXPLAIN ANALYZE 3회, ms)

| 쿼리 | ASC (backward) | DESC (forward) |
|---|---|---|
| A 브랜드+좋아요순 (page 0) | 0.41 / 0.53 / 0.78 | 0.10 / 0.11 / 0.11 |
| B 전체+좋아요순 (page 0) | 0.31 / 0.57 / 0.43 | (0.34) / 0.09 / 0.08 |
| A 딥오프셋 (offset 1000) | **20.4 / 34.5 / 13.6** | **(12.9) / 2.18 / 2.16** |

> 괄호는 첫 회(워밍업) 값. page 0 은 20행만 읽어 둘 다 sub-ms, 체감차 미미.

### 8.3 결론

- **page 0(20행)**: ASC·DESC 차이는 노이즈 수준. 다만 DESC(forward)가 일관되게 약간 빠르고 안정적.
- **딥 오프셋(1,020행 스캔)**: backward scan 페널티가 뚜렷 — ASC ~13~34 ms vs DESC ~2.2 ms
  (워밍업 제외 시 **약 6~15배**). InnoDB 의 페이지 링크/read-ahead 가 forward 순회에 최적화돼,
  **스캔량이 커질수록 backward scan 이 불리**하다.
- **추가로, mixed-direction 정렬**(예: `likes_count DESC, id ASC`)은 backward scan(전체 키 반전)으로도,
  forward scan 으로도 충족 못 해 **방향 지정 인덱스가 아니면 filesort 가 강제**된다. 즉 DESC 인덱스의 가치는
  "조금 더 빠름"이 아니라 그런 정렬에서 **filesort 를 없애는 유일한 수단**이라는 점에 있다.

### 8.4 결정 — DESC 인덱스 채택

위 두 가지(딥 페이지 backward 페널티 + mixed-direction 정렬) 때문에 **DESC 인덱스를 채택**한다.
`@Index` 로는 방향을 못 박으므로 적용은 DDL 로 한다:
- `ProductEntity` 의 `@Index` 제거 (잘못된 ASC 인덱스 생성 방지)
- local/test: `resources/import.sql` (ddl-auto: create 직후 실행) — **통합테스트에서 인덱스 생성 검증 완료**
- prd: `docs/week5/migration_product_indexes.sql`
- 딥 오프셋은 별개 과제로 **keyset 페이지네이션**에서 근본 해결한다.

## 9. 부록 — 단일 컬럼 인덱스 vs 복합 인덱스

"왜 복합 인덱스여야 하나"를 검증하기 위해, 복합 인덱스를 빼고 단일 컬럼 인덱스 3개
(`brand_id`, `likes_count`, `deleted_at`)만 두고 옵티마이저의 선택을 관찰했다.

- 스크립트: [`08_indexes_single.sql`](../../benchmark/sql/08_indexes_single.sql) · [`09_explain_single.sql`](../../benchmark/sql/09_explain_single.sql)
- 원본: [`results/09_explain_single.txt`](../../benchmark/results/09_explain_single.txt)

### 9.1 쿼리 A — index_merge intersect + **filesort 잔존**

```
-> Sort: likes_count DESC, id DESC, limit ...        ← filesort 그대로
    -> Filter: (brand_id=1 and deleted_at is null)
        -> Intersect rows sorted by row ID
            -> Index range scan using idx_brand_id   (16,858 rows)
            -> Index range scan using idx_deleted_at (110,636 rows)   ← 11만 행 읽음
```
- 두 단일 인덱스를 **index_merge(교집합)** 로 필터하지만, `likes_count` 정렬은 인덱스로 못 풀어
  **filesort 가 남는다**. 실행 시간 **218~262 ms** — 무인덱스(213 ms)와 사실상 동일.

### 9.2 쿼리 B — `idx_likes_count` reverse, **filesort 제거** (단, 분포 의존적)

```
-> Limit: 20
    -> Filter: (deleted_at is null)
        -> Index scan using idx_likes_count (reverse)   (rows=22)
```
- 정렬 컬럼 인덱스를 backward 로 훑고 `deleted_at` 을 행마다 필터 → **filesort 없음**, 실행 시간
  **0.15~0.25 ms** (복합과 동급).
- **단, 이건 데이터 분포 덕이다.** `deleted_at IS NULL` 이 비선택적(92% 통과)이라 22행만에 활성 20행이
  채워졌을 뿐. soft-delete 비율이 높았다면 멀리까지 스캔했을 것이고, A 처럼 **선택적 등치 필터가 끼면
  이 전략 자체가 불가**하다.

### 9.3 종합 비교

| 쿼리 | 무인덱스 | 단일 컬럼 인덱스 | 복합 DESC 인덱스 |
|---|---|---|---|
| A 브랜드+좋아요순 | 213 ms · 풀스캔+filesort | **218~262 ms · merge + filesort 잔존** (11만 행) | **0.12 ms · filesort 없음** |
| B 전체+좋아요순 | 328 ms · 풀스캔+filesort | 0.15~0.25 ms · likes 인덱스 reverse (분포 덕, 취약) | **0.16 ms · filesort 없음** |

**결론**: 단일 인덱스는 B 에서 *운 좋게* 통했을 뿐(데이터 분포 의존), A 에서는 무인덱스와 다를 바 없다.
**복합 인덱스만이 두 쿼리 모두에서 분포와 무관하게** 풀스캔·filesort 를 제거한다 → §8 의 DESC 복합 채택이 옳음을 재확인.

## 10. 재현 방법

```bash
docker-compose -f ./docker/infra-compose.yml up -d   # MySQL 기동
bash benchmark/run.sh                                 # 시딩 + ASC 인덱스 EXPLAIN 전후 측정
# 결과: benchmark/results/*.txt

# 이어서 (run.sh 로 시딩된 상태에서) — 인덱스만 교체하며 비교
docker exec -i docker-mysql-1 mysql -uroot -proot --table   < benchmark/sql/06_indexes_desc.sql    # DESC 복합
docker exec -i docker-mysql-1 mysql -uroot -proot --vertical < benchmark/sql/07_explain_desc.sql
docker exec -i docker-mysql-1 mysql -uroot -proot --table   < benchmark/sql/08_indexes_single.sql  # 단일 컬럼
docker exec -i docker-mysql-1 mysql -uroot -proot --vertical < benchmark/sql/09_explain_single.sql
```
