# [Stage 2] 비정규화 (products.like_count) 측정 보고서

> 환경·시나리오 정의·측정 규약은 `00-setup.md`. 베이스라인은 `01-baseline.md`.

## 이번 단계 한눈에

- **무엇을 바꿨나**: 매 조회 시 좋아요 수를 세던 스칼라 서브쿼리(`SELECT COUNT(*) FROM likes …`)를 제거하고, `products.like_count` 비정규화 컬럼을 직접 조회/정렬. 좋아요 등록·취소 시 원자적 UPDATE로 컬럼을 동기화.
- **캐시 상태**: off
- **기대**: 좋아요순(S1·S2)에서 정렬 키로 쓰이던 상관 서브쿼리가 사라지면 베이스라인의 DNF가 풀린다. 단 인덱스가 아직 없어 정렬은 filesort로 남는다.

---

## 시나리오별 상세

### S1 : 좋아요순 + 전역 + 1페이지

> 메인의 "좋아요 많은 순" 전체 상품 첫 페이지.

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | ALL(풀스캔) | 없음 | 99,433 | Using where; Using filesort |
| 브랜드 b | eq_ref | PRIMARY | 1 | Using where |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (Table scan) | 100,000 | 1 | ~29ms |
| Sort (like_count DESC, id DESC) | 20 | 1 | 62.4ms 누적 |
| 브랜드 b (PK 단건 lookup) | 1 | 20 | 0.01ms/회 |

- **단건 총 실측: 62.5ms**

**왜 추정과 실측이 다른가**: 옵티마이저는 정렬 대상을 `rows=99,433`(통계 추정)으로 봤지만, 실제로는 10만 행을 모두 스캔해 메모리에서 정렬한 뒤 상위 20개만 취한다. 베이스라인에서 7초~DNF의 주범이던 "행마다 좋아요를 세는 서브쿼리(loops=20)"가 사라져, 남은 비용은 **10만 행 풀스캔 + filesort**뿐이라 62.5ms로 떨어졌다.

**API 성능 — 동시 50명**

| p50 | p95 | p99 | 에러율 |
|---|---|---|---|
| 2.2s | 2.79s | 3.14s | 0% |

> 단건은 62.5ms인데 동시 50명에선 p50 2.2s — **풀스캔+filesort가 동시성에서 직렬 병목**이다. 50명이 같은 10만 행을 동시에 스캔·정렬하느라 CPU·정렬 메모리를 경합해 처리량이 22.8 req/s로 묶인다. 에러는 0%(베이스라인 100% 에러에서 회복).

---

### S2 : 좋아요순 + 인기 브랜드(847) + 1페이지

> 인기 브랜드(847)관의 "좋아요 많은 순" 첫 페이지.

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 브랜드 b | const | PRIMARY | 1 | Using filesort |
| 상품 p | ALL(풀스캔) | 없음 | 99,433 (filtered 1%) | Using where |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (Table scan) | 100,000 | 1 | ~21ms |
| Filter (brand_id=847) | 1,134 | 1 | 24.9ms 누적 |
| Sort (top-N, 20행/chunk) | 20 | 1 | 25.1ms 누적 |

- **단건 총 실측: 25.1ms**

**왜 추정과 실측이 다른가**: 브랜드 필터가 있어도 `brand_id` 인덱스가 없어 10만 행을 풀스캔한 뒤 `brand_id=847`로 거른다(1,134행). 정렬 대상이 전역(10만)보다 훨씬 작은 1,134행이라, top-N 힙 정렬로 S1보다 빠른 25.1ms가 나온다. 옵티마이저가 `filtered 1%`로 추정한 게 실제 1,134/100,000과 대체로 맞다.

**API 성능 — 동시 50명**

| p50 | p95 | p99 | 에러율 |
|---|---|---|---|
| 329ms | 560ms | 887ms | 0% |

> 정렬 대상이 1,134행으로 작아 동시 50명에서도 p95 560ms로 가장 양호하다. 그래도 매 요청이 10만 행 풀스캔을 동반해 S4(PK)보다 훨씬 느리다 — `brand_id` 인덱스가 다음 단계 과제.

---

### S3 : 최신순 + 전역 + 1페이지

> 기본 정렬(최신순) 전체 상품 첫 페이지.

**나가는 쿼리**
```sql
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20 OFFSET 0;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | ALL(풀스캔) | 없음 | 99,433 | Using where; Using filesort |
| 브랜드 b | eq_ref | PRIMARY | 1 | Using where |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품 p (Table scan) | 100,000 | 1 | ~22ms |
| Sort (created_at DESC, id DESC) | 20 | 1 | 46.8ms 누적 |
| 브랜드 b (PK 단건 lookup) | 1 | 20 | 0.002ms/회 |

- **단건 총 실측: 46.8ms**

**왜 추정과 실측이 다른가**: S1과 구조가 같다 — 정렬 키만 `like_count`에서 `created_at`으로 바뀌었을 뿐 둘 다 인덱스가 없어 10만 행 풀스캔 + filesort. 베이스라인에서 S3가 ~7s였던 건 정렬이 아니라 **투영(SELECT)에 박힌 COUNT 서브쿼리가 20번 반복**됐기 때문인데, 그 서브쿼리가 사라져 46.8ms가 됐다.

**API 성능 — 동시 50명**

| p50 | p95 | p99 | 에러율 |
|---|---|---|---|
| 2.26s | 2.86s | 3.33s | 0% |

> S1과 거의 같은 양상 — 단건 46.8ms지만 동시 50명에선 p50 2.26s. 전역 10만 행 풀스캔+filesort가 동시성 병목. `created_at` 인덱스가 다음 단계 과제.

---

### S4 : 상세 + 단건(45577)

> 핫 상품(45577) 상세 페이지.

**나가는 쿼리**
```sql
SELECT p.id, p.name, p.description, b.id, b.name, p.price, p.stock, p.like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.id = 45577 AND p.deleted_at IS NULL;
```

**EXPLAIN — 옵티마이저 계획 (추정)**

| 테이블 | 접근방식 | 인덱스 | 추정행 | 비고(Extra) |
|---|---|---|---|---|
| 상품 p | const | PRIMARY | 1 | - |
| 브랜드 b | const | PRIMARY | 1 | - |

**EXPLAIN ANALYZE — 실제 실행 (실측)**

| 테이블 | 실측행 | 반복(loops) | 실측시간 |
|---|---|---|---|
| 상품·브랜드 (PK const 조회) | 1 | 1 | ~0ms |

- **단건 총 실측: ~0ms (PK 단건)**

**왜 추정과 실측이 다른가**: 추정과 실측이 일치한다. `id`(PK) 단건이라 옵티마이저가 `const`로 단정하고, 실제로도 인덱스 한 번에 1행을 집어 든다. 베이스라인에서 ~50ms였던 건 좋아요 COUNT 서브쿼리(좋아요 5,000행 스캔) 때문인데, 비정규화로 그마저 컬럼 한 번 읽기가 됐다.

**API 성능 — 동시 50명**

| p50 | p95 | p99 | 에러율 |
|---|---|---|---|
| 13.88ms | 19.03ms | 24.33ms | 0% |

> PK 단건이라 동시 50명에서도 경합이 없어 3,499 req/s를 처리한다. 베이스라인(p95 707ms)에서 35배 이상 개선.

---

## 이 단계 총괄

> 4개 시나리오를 한눈에. 실행계획 핵심 + API 성능을 한 표로.

| 시나리오 | 접근방식 | 정렬 | 실측시간(단건) | p50 | p95 | p99 |
|---|---|---|---|---|---|---|
| S1 좋아요·전역 | 풀스캔 | filesort | 62.5ms | 2.2s | 2.79s | 3.14s |
| S2 좋아요·브랜드 | 풀스캔(필터) | filesort(top-N) | 25.1ms | 329ms | 560ms | 887ms |
| S3 최신·전역 | 풀스캔 | filesort | 46.8ms | 2.26s | 2.86s | 3.33s |
| S4 상세·단건 | PK const | - | ~0ms | 13.88ms | 19.03ms | 24.33ms |

## 직전 단계 대비 & 해석

- **무엇이 왜 좋아졌나**(쉬운 말로): 좋아요 수를 "조회할 때마다 세는" 대신 "미리 칸에 적어두고 그 칸만 읽도록" 바꿨다. 그 결과 베이스라인에서 30초를 넘겨 **응답조차 못 주던 목록 3종(S1·S2·S3)이 전부 정상 응답**하게 됐고, 동시 50명에서 **100% 에러 → 0% 에러**가 됐다. 단건 기준으로 보면 좋아요순(S1) DNF→62.5ms, 최신순(S3) 7s→46.8ms, 상세(S4) 50ms→0ms.
- **남은 한계 / 다음 단계 동기**: 단건은 수십 ms지만 **전역 목록(S1·S3)은 동시 50명에서 p50 2.2초**다. 원인은 매 요청이 10만 행을 **풀스캔하고 filesort**로 정렬하기 때문 — 50명이 같은 작업을 동시에 하면 CPU·정렬 메모리를 경합해 처리량이 22 req/s로 묶인다. 즉 "서브쿼리"라는 병목을 걷어냈더니 그 아래 가려져 있던 "**인덱스 부재(풀스캔·filesort)**"가 드러났다. 다음 단계(인덱스)에서 `like_count`·`brand_id`·`created_at`에 인덱스를 걸어 풀스캔과 filesort를 제거하는 것이 목표다.
