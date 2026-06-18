# [Stage 1] 베이스라인 (As-Is, 최적화 0) 측정 보고서

> 모든 비교의 **원점**. 현재 코드(스칼라 서브쿼리 기반 like 카운트)를 변경 없이 측정.
> 환경·시나리오·측정 규약은 `00-setup.md`. 쿼리는 `../measurement/sql/`.

## 이번 단계 한눈에

- **무엇을 바꿨나**: 없음 (As-Is). 인덱스는 PK + `uk_likes(user_id, product_id)` 뿐 — `brand_id`·`price`·`created_at`·`likes.product_id` 인덱스 전무.
- **캐시 상태**: off
- **기대**: 좋아요 수를 **매 조회 COUNT 서브쿼리**로 세는 구조라, 목록은 느리고 좋아요순 정렬은 폭발할 것.

**한 줄 결론**: 동시 50명에서 **상세(S4)만 살아남고**, 목록 3종(S1·S2·S3)은 7초~무한대 쿼리가 커넥션 풀(40)을 고갈시켜 **100% 에러**로 붕괴한다.

---

## 시나리오별 상세

### S1 : 좋아요순 + 전역 + 1페이지

> 메인의 "좋아요 많은 순" 전체 상품 첫 페이지.

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) DESC, p.id DESC
LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | 풀스캔(ALL) | 없음 | 99,433 | Using where; **Using filesort** |
| 브랜드 b | eq_ref | PRIMARY | 1 | Using where |
| 좋아요 l (서브쿼리 ②, SELECT용) | index | uk_likes | 2,809,240 | dependent subquery |
| 좋아요 l (서브쿼리 ③, ORDER BY용) | index | uk_likes | 2,809,240 | dependent subquery |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| — | — | — | **DNF (>30초)** |

**왜 추정과 실측이 다른가**: COUNT 서브쿼리가 **ORDER BY에도** 들어가(서브쿼리 ③), 정렬 후보 행마다(전역 약 10만) COUNT를 재평가한다. EXPLAIN의 평평한 "rows=2.81M"엔 이게 **행 수만큼 반복**된다는 게 안 보인다. 실제로는 10만 × 2.81M 규모 → 30초를 한참 넘겨 측정 불가.

**API 성능 — 동시 50명**

| p50 | p95 | p99 | 에러율 |
|---|---|---|---|
| connection-timeout(>3초) | connection-timeout(>3초) | connection-timeout(>3초) | **100% 에러** |

> 모든 요청이 ~3초에 `connection-timeout`으로 500. 쿼리가 끝나지 않아 풀이 영구 고갈. p값은 서비스 시간이 아니라 타임아웃 벽이다.

---

### S2 : 좋아요순 + 인기 브랜드(847) + 1페이지

> 인기 브랜드(847)관의 "좋아요 많은 순" 첫 페이지.

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) DESC, p.id DESC
LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 브랜드 b | const | PRIMARY | 1 | Using filesort |
| 상품 p | 풀스캔(ALL) | 없음 | 99,433 | Using where (brand_id 인덱스 없어 풀스캔) |
| 좋아요 l (서브쿼리 ②) | index | uk_likes | 2,809,240 | dependent subquery |
| 좋아요 l (서브쿼리 ③) | index | uk_likes | 2,809,240 | dependent subquery |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| — | — | — | **DNF (>30초, 31초 캡에서 ERROR 3024 확인)** |

**왜 추정과 실측이 다른가**: 브랜드 필터로 후보가 1,134행으로 줄어도, `brand_id` 인덱스가 없어 **상품 전체를 풀스캔**한 뒤 거른다. 게다가 S1과 똑같이 COUNT가 ORDER BY에 있어 후보 1,134행마다 2.81M 스캔 → 31초 캡 초과. 추정엔 이 반복이 안 보인다.

**API 성능 — 동시 50명**

| p50 | p95 | p99 | 에러율 |
|---|---|---|---|
| connection-timeout(>3초) | connection-timeout(>3초) | connection-timeout(>3초) | **100% 에러** |

---

### S3 : 최신순 + 전역 + 1페이지

> 기본 정렬(최신순) 전체 상품 첫 페이지.

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | 풀스캔(ALL) | 없음 | 99,433 | Using where; **Using filesort** |
| 브랜드 b | eq_ref | PRIMARY | 1 | Using where |
| 좋아요 l (서브쿼리 ②, SELECT용) | index | uk_likes | 2,809,240 | dependent subquery (1개) |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 단계 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 풀스캔 | 100,000 | 1 | 0.86 → 35.1ms |
| 정렬(filesort) → LIMIT 20 | 20 | 1 | 69.3 → 69.4ms |
| 좋아요 COUNT 집계 (서브쿼리) | 1 | **20** | 루프당 ~629ms |
| └ 좋아요 커버링 인덱스 스캔 | **2,910,000** | **20** | 루프당 최대 511ms |

- **단건 총 실측: ~7.0초**

**왜 추정과 실측이 다른가**: 정렬엔 서브쿼리가 없어 풀스캔+filesort는 75ms로 끝난다. 문제는 **SELECT의 COUNT 서브쿼리가 LIMIT 후 20행마다 1번씩(loops=20)** 실행되며, 매번 좋아요 2.91M행을 훑는다는 것. EXPLAIN 추정의 "rows=2.81M"엔 이 **20회 반복**이 안 보여 한 번 보는 것처럼 착시를 준다. 실제 2.91M × 20 ≈ 58M행 스캔 → 7초.

**API 성능 — 동시 50명**

| p50 | p95 | p99 | 에러율 |
|---|---|---|---|
| connection-timeout(>3초) | connection-timeout(>3초) | connection-timeout(>3초) | **100% 에러** |

> 단건은 7초로 측정되지만(서비스 시간 존재), 동시 50명에선 7초 쿼리가 풀 40개를 영구 점유 → 나머지는 3초 타임아웃. **동시성에선 붕괴**.

---

### S4 : 상세 + 단건(45577)

> 핫 상품(좋아요 5,000) 상세 페이지.

**나가는 쿼리**
```sql
SELECT p.id, p.name, p.description, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.id = 45577 AND p.deleted_at IS NULL;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | const | PRIMARY | 1 | — |
| 브랜드 b | const | PRIMARY | 1 | — |
| 좋아요 l (서브쿼리 ②) | range | uk_likes | 280,924 | Using index for **skip scan** |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 단계 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품·브랜드 PK 상수 조회 | 1 | 1 | ~0ms |
| 좋아요 COUNT 집계 (서브쿼리) | 1 | 1 | 44.8ms |
| └ 좋아요 skip scan | 5,000 | 1 | 0.04 → 44.5ms |

- **단건 총 실측: ~50ms**

**왜 추정과 실측이 다른가**: `product_id`가 **상수**(45577)라 옵티마이저가 `uk_likes`로 skip scan을 골라 5,000행만 본다. 추정 행수(280,924)는 skip scan 비용을 보수적으로 잡은 값이고, 실측은 5,000행 1회(loops=1). 상관 변수가 아니라 상수라 목록과 결정적으로 다르다.

**API 성능 — 동시 50명**

| p50 | p95 | p99 | 에러율 |
|---|---|---|---|
| 435ms | 707ms | 905ms | **0% (3,304건 전부 200)** |

> 단건 50ms지만 동시 50명에선 COUNT 서브쿼리(45ms×50동시)+경합으로 p50 435ms. 그래도 유일하게 정상 처리(~109 rps).

---

## 이 단계 총괄 (베이스라인)

| 시나리오 | 접근방식 | 정렬 | 실측시간(단건) | p50 | p95 | p99 | 동시50 에러율 |
|---|---|---|---|---|---|---|---|
| S1 좋아요·전역 | 풀스캔 | filesort | **DNF (>30s)** | — | — | — | 100% |
| S2 좋아요·브랜드 | 풀스캔 | filesort | **DNF (>30s)** | — | — | — | 100% |
| S3 최신·전역 | 풀스캔 | filesort | ~7.0s | connection-timeout(>3초) | connection-timeout(>3초) | connection-timeout(>3초) | 100% |
| S4 상세·단건 | PK const | — | ~50ms | 435ms | 707ms | 905ms | 0% |

> S1·S2·S3의 p값은 **서비스 시간이 아니라 3초 커넥션 타임아웃 벽**이다. 목록은 동시 50명에서 사실상 무응답(DNF).

## 직전 단계 대비 & 해석

- **무엇이 왜 느린가**(쉬운 말로): 상품마다 "좋아요가 몇 개냐"를 **조회할 때마다 처음부터 다시 센다**(COUNT 서브쿼리). 좋아요 데이터가 290만 줄이라 한 번 세는 데도 오래 걸리는데,
  - **좋아요순(S1·S2)**: "가장 좋아요 많은 순"으로 줄 세우려면 **모든 상품의 좋아요를 다 세야** 정렬이 된다 → 사실상 영원히 안 끝남(DNF).
  - **최신순(S3)**: 정렬은 빨리 되지만, 보여줄 20개의 좋아요를 세느라 한 건에 7초.
  - **상세(S4)**: 한 상품만 보면 되니 50ms로 빠름. 단 동시 사용자가 몰리면 이마저 느려진다.
- **남은 한계 / 다음 단계 동기**:
  - **Stage 2 (비정규화)**: 좋아요 수를 `products.like_count` 컬럼에 **미리 계산해 둬서** "셀 필요 없음"으로 만든다 → S1·S2·S3의 서브쿼리 비용 소멸 예상(단 인덱스 전이라 정렬 filesort는 잔존).
  - **Stage 3 (인덱스)**: `brand_id`·정렬키 복합 인덱스로 풀스캔+filesort 제거.
  - **Stage 4 (캐시)**: 자주 같은 요청(상세·인기 목록)은 DB까지 안 가게.
