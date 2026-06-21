# [Benchmark] 상품 목록 조회 인덱스 설계 — 단일 vs 복합, 데이터 분포에 따른 선택 기준 (5주차 · 6팀 · 변승진)

## TL;DR

인덱스 추가만으로 `type: ALL` → `type: index/ref`, rows: 99,516 → 20, filesort 제거를 달성했다.
단, 인덱스보다 먼저 해결해야 할 것이 있었다 — OR 조건이 남아 있으면 인덱스를 추가해도 옵티마이저가 풀스캔을 선택한다.
1M 건으로 확장하면 브랜드 분포에 따라 옵티마이저 선택이 달라지고, 딥 페이지에서는 인덱스 전략 자체가 뒤집힌다.

---

## Question (무엇을 비교하는가)

브랜드 필터(`brand_id`) + 정렬(`like_count`, `created_at`, `price`) 조합 조회에서,
어떤 인덱스 설계가 실행 계획을 최적화하는가.

후보:

- A: 인덱스 없음 (기존)
- B: 단일 컬럼 인덱스 (`like_count`, `created_at`, `price` 각각)
- C: 복합 인덱스 (`brand_id + 정렬 컬럼`)

---

## Setup (측정 환경)

측정은 두 가지 데이터 구성으로 나눠 진행했다.

**소규모 — 인덱스 설계 검증용**

| 테이블 | 건수 | 분포 |
|--------|-----:|------|
| brands | 20 | 균등 |
| products | 100,000 | 브랜드당 약 5,000건, like_count 제곱 분포, created_at 최근 2년 균등 랜덤 |
| stocks | 100,000 | 상품 1:1 |

**대규모 — 데이터 분포·OFFSET 영향 검증용**

| 테이블 | 건수 | 분포 |
|--------|-----:|------|
| brands | 100 | 균등 / 비균등 두 가지로 실험 |
| products | 1,000,000 | 균등: 브랜드당 약 10,000건 / 비균등: 오래된 브랜드일수록 상품 많고 created_at이 과거에 집중 |
| stocks | 1,000,000 | 상품 1:1 |

- **DB**: MySQL 8.x (InnoDB)
- **측정 방법**: `EXPLAIN` 실행 계획 분석 (`type`, `key`, `rows`, `Extra`), `EXPLAIN ANALYZE`로 추정치 대비 실제 실행 시간 검증
- **쿼리 조건**: `WHERE deleted_at IS NULL`, `ORDER BY {정렬 컬럼}`, `LIMIT 20`

---

## Results (측정 결과)

### 1단계 — 인덱스 없음 (10만 건 기준)

| 케이스 | type | key | rows | filesort |
|--------|------|-----|-----:|:--------:|
| 전체 좋아요순 | ALL | - | 99,516 | ✅ |
| 전체 최신순 | ALL | - | 99,516 | ✅ |
| 전체 가격순 | ALL | - | 99,516 | ✅ |
| 브랜드 + 좋아요순 | ALL | - | 99,516 | ✅ |
| 브랜드 + 최신순 | ALL | - | 99,516 | ✅ |
| 브랜드 + 가격순 | ALL | - | 99,516 | ✅ |

모든 케이스에서 풀스캔 + filesort. 10만 건 전체를 읽고 메모리에서 정렬한다.

---

### 2단계 — like_count 인덱스 추가 (10만 건 기준)

`idx_products_likes_desc (like_count DESC, created_at DESC)`,
`idx_products_brand_likes (brand_id, like_count DESC, created_at DESC)` 추가.

| 케이스 | type | key | rows | filesort |
|--------|------|-----|-----:|:--------:|
| 전체 좋아요순 | index | idx_products_likes_desc | **20** | ❌ |
| 브랜드 + 좋아요순 | ref | idx_products_brand_likes | **9,116** | ❌ |

좋아요순에서 filesort가 제거됐다. 최신순·가격순은 여전히 풀스캔이라 추가 분석이 필요하다.

---

### 3단계 — 정렬 컬럼별 인덱스 전부 추가 (10만 건 기준, 최종)

`idx_products_created_at`, `idx_products_brand_created_at`,
`idx_products_price`, `idx_products_brand_price` 추가.

| 케이스 | type | key | rows | filesort |
|--------|------|-----|-----:|:--------:|
| 전체 좋아요순 | index | idx_products_likes_desc | **20** | ❌ |
| 전체 최신순 | index | idx_products_created_at | **20** | ❌ |
| 전체 가격순 | index | idx_products_price | **20** | ❌ |
| 브랜드 + 좋아요순 | ref | idx_products_brand_likes | **9,116** | ❌ |
| 브랜드 + 최신순 | ref | idx_products_brand_created_at | **5,032** | ❌ |
| 브랜드 + 가격순 | ref | idx_products_brand_price | **5,032** | ❌ |

전체 6개 케이스에서 filesort 제거. `type: index`는 인덱스 순서대로 스캔하며 LIMIT에서 조기 종료, `type: ref`는 brand_id로 좁힌 뒤 정렬 인덱스를 활용한다.

---

### 4단계 — 1M 건으로 확장, 데이터 분포별 비교

인덱스 설계를 10만 건에서 검증한 뒤, 100만 건으로 확장해 데이터 분포가 옵티마이저 선택에 어떤 영향을 주는지 확인했다.

| 케이스 | 20브랜드 균등 1M | 100브랜드 비균등 1M |
|--------|:---------------:|:-------------------:|
| 전체 좋아요순 | index / 20 | index / 20 |
| 전체 최신순 | index / 20 | index / 20 |
| 전체 가격순 | index / 20 | index / 20 |
| 브랜드 + 좋아요순 | ref / 93,736 | ref / 18,916 |
| 브랜드 + **최신순** | **index / 211** | **index / 1,050** |
| 브랜드 + 가격순 | ref / 93,736 | ref / 18,916 |

전체 정렬 3종은 데이터 규모·분포와 무관하게 rows = 20으로 안정적이다. 브랜드 필터 조합에서는 rows가 크게 늘어나고, 유독 **브랜드 + 최신순**만 동작이 다르다.

**브랜드 + 최신순이 나머지와 다른 이유:**

- **20브랜드 균등** — `created_at` 인덱스를 순서대로 스캔하면 brand_id=1이 1/20 확률로 고르게 분포해 있다. rows=211만 훑으면 20건을 찾을 수 있다고 옵티마이저가 추정. `idx_products_brand_created_at`(ref / 93,736)보다 저렴하다고 판단해 단일 인덱스 스캔 선택.

- **100브랜드 비균등** — brand_id=1은 가장 오래된 브랜드라 최근 구간의 `created_at` 범위에서 희박하다(filtered=0.19%). rows=1,050으로 늘어나지만 여전히 `idx_products_brand_created_at`(ref / 18,916)보다 낮아 단일 인덱스 스캔 유지.

**결론:** 10만 건 균등 분포에서 rows=5,032로 `idx_products_brand_created_at`을 쓰던 것이 1M 균등에서는 rows=211로 단일 인덱스로 바뀌었다. 이는 인위적 균등 분포가 만든 현상이다. 실 운영에서 brand_id와 created_at 간 상관관계가 강해질수록 단일 인덱스 스캔 비용이 올라가 복합 인덱스로 전환되는 임계점에 가까워진다. `idx_products_brand_created_at`이 필요한 이유다.

---

### 5단계 — EXPLAIN ANALYZE로 본 실제 실행 시간 (1M 건 실측)

`rows`는 옵티마이저의 추정치일 뿐, 실제 비용과 비례하는지는 별도로 확인해야 한다. MySQL 8 컨테이너에 브랜드 100개·상품 100만 건(브랜드 1이 전체의 약 10%를 차지하도록 치우치게 생성, 최종 인덱스 6종 적용)을 직접 적재하고 `EXPLAIN ANALYZE`로 `actual rows`·`actual time`을 측정했다.

| 케이스 | 추정 rows | actual rows | actual time | key |
|--------|----------:|------------:|------------:|-----|
| 전체 좋아요순 | 20 | 20 | 0.34 ms | `idx_products_likes_desc` |
| 전체 최신순 | 20 | 20 | 0.07 ms | `idx_products_created_at` |
| 전체 가격순 | 20 | 20 | 0.16 ms | `idx_products_price` |
| 브랜드(1) + 좋아요순 (`ref`) | 207,606 | 20 | 0.63 ms | `idx_products_brand_likes` |
| 브랜드(1) + 가격순 (`ref`) | 207,606 | 20 | 0.58 ms | `idx_products_brand_price` |
| 브랜드(1) + 최신순, OFFSET 0 | 207,606 | 20 | 0.43 ms | `idx_products_brand_created_at` |
| 브랜드(1) + 최신순, OFFSET 100 | 207,606 | 120 | 0.63 ms | `idx_products_brand_created_at` |
| 브랜드(1) + 최신순, OFFSET 1,000 | 207,606 | 1,020 | 6.94 ms | `idx_products_brand_created_at` |

**확인된 것:**

- 추정 rows(207,606)는 옵티마이저가 "브랜드 1 전체"를 가정한 상한값이고, `LIMIT`이 있으면 실제로는 `actual rows = OFFSET + LIMIT`만큼만 읽고 멈춘다. 추정치와 실측치가 크게 벌어지는 게 비정상이 아니라, `LIMIT` 조기 종료가 정상 동작하고 있다는 신호다.
- OFFSET 0 → 100 → 1,000으로 갈수록 actual rows가 20 → 120 → 1,020으로, actual time이 0.43 → 0.63 → 6.94ms로 거의 선형으로 늘었다. 딥 페이지 비용이 rows 증가와 함께 실제 시간으로도 그대로 나타난다.
- 이 데이터셋에서는 브랜드 1이 "최근 30일 생성 상품 0건"으로 극단적으로 분리돼 있어, OFFSET이 커져도 `idx_products_brand_created_at` ref 전략이 끝까지 유지됐다(4단계에서 본 "단일 인덱스로 전략이 뒤집히는" 현상은 brand-date 상관관계가 이보다 약할 때 나타난다 — 상관관계가 강할수록 ref가 처음부터 유리해 뒤집힐 여지조차 없어진다는 뜻으로, 4단계 결론을 반대 방향에서 재확인한 셈이다).
- 측정은 컨테이너 기동 직후 데이터 적재 → `ANALYZE TABLE` → 곧바로 실행한 값으로, 별도 워밍업 없이 1회 측정한 값이다. 반복 측정으로 분산까지 보는 건 Future Work로 남긴다.

---

## 인덱스보다 먼저 해결해야 했던 것 — OR 조건

인덱스를 추가하기 전에 쿼리에 OR 조건이 있었다.

```sql
WHERE deleted_at IS NULL AND (:brandId IS NULL OR brand_id = :brandId)
```

이 상태에서 인덱스를 추가해도 EXPLAIN 결과가 바뀌지 않았다. 옵티마이저는 "brandId가 null일 수도 있다"고 판단해 인덱스를 타지 않고 풀스캔을 선택했다.

**해결**: 쿼리를 두 메서드로 분리했다.

```java
findAllActive(pageable)                    // WHERE deleted_at IS NULL
findAllActiveByBrandId(brandId, pageable)  // WHERE deleted_at IS NULL AND brand_id = ?
```

OR 조건 자체를 없애자 옵티마이저가 비로소 인덱스를 선택했다. 인덱스 설계보다 쿼리 구조가 선행 조건이었다.

---

## deleted_at IS NULL — selectivity가 낮아도 인덱스가 효과적인 이유

`deleted_at IS NULL`은 삭제되지 않은 상품, 즉 거의 전체에 해당한다. selectivity가 매우 낮은 컬럼이다. 처음에는 "이 컬럼이 조건에 있으면 LIMIT이 있어도 인덱스가 비효율적이지 않을까"라는 의문이 있었다.

EXPLAIN으로 확인해보니 `LIMIT 20`이 있으면 인덱스를 정렬 순서대로 읽다가 조건에 맞는 20개를 찾는 즉시 멈춘다(`type: index`). selectivity가 낮아도 LIMIT이 조기 종료 조건으로 작동해 실질 읽기 비용이 작았다.

selectivity 문제는 LIMIT 없는 전체 스캔에서만 인덱스를 무력화한다. 페이지네이션 쿼리에서는 정렬 인덱스가 충분히 효과적이다.

---

## OFFSET이 커지면 결과가 달라진다 (1M 100브랜드 비균등 기준)

지금까지 EXPLAIN은 모두 OFFSET 없는 1페이지 기준이었다. 실제 Pageable은 `LIMIT 20 OFFSET (page * 20)`을 생성한다.

### 전체 최신순 — OFFSET에 정비례

| OFFSET | rows |
|-------:|-----:|
| 0 | 20 |
| 1,000 | 1,020 |
| 10,000 | 10,020 |
| 50,000 | 50,020 |

rows = OFFSET + LIMIT. 페이지가 깊어질수록 인덱스 스캔 범위가 선형으로 늘어난다.

### 브랜드 + 최신순 — OFFSET에 따라 전략 자체가 바뀐다

| OFFSET | type | key | rows | filesort |
|-------:|------|-----|-----:|:--------:|
| 0 | index | idx_products_created_at | 1,050 | ❌ |
| 100 | index | idx_products_created_at | 6,304 | ❌ |
| 1,000 | ref | idx_products_brand_likes | 18,916 | ✅ |

OFFSET 1,000에서 옵티마이저가 `created_at` 인덱스 스캔을 포기하고 brand ref + filesort로 전략을 바꿨다. brand_id=1인 행을 1,020개 찾으려면 `created_at` 인덱스를 100만 행 가까이 스캔해야 하는데, brand ref로 18,916행을 가져와 정렬하는 쪽이 더 저렴하다고 판단한 것이다. filesort가 다시 등장했다.

이것이 OFFSET 페이지네이션의 구조적 한계다. 인덱스를 아무리 잘 설계해도 딥 페이지에서는 rows가 선형 증가하고, 특정 조합에서는 인덱스 전략 자체가 뒤집혀 filesort까지 부활한다.

---

## Decision

### 최종 인덱스 구성

| 인덱스명 | 컬럼 | 커버 케이스 |
|----------|------|-------------|
| `idx_products_likes_desc` | `(like_count DESC, created_at DESC)` | 전체 좋아요순 |
| `idx_products_brand_likes` | `(brand_id, like_count DESC, created_at DESC)` | 브랜드 + 좋아요순 |
| `idx_products_created_at` | `(created_at DESC)` | 전체 최신순 |
| `idx_products_brand_created_at` | `(brand_id, created_at DESC)` | 브랜드 + 최신순 |
| `idx_products_price` | `(price ASC)` | 전체 가격순 |
| `idx_products_brand_price` | `(brand_id, price ASC)` | 브랜드 + 가격순 |

### 쓰기 비용 트레이드오프

| 인덱스 | 갱신 시점 | 빈도 | 허용 근거 |
|--------|-----------|:----:|----------|
| like_count 관련 2개 | 좋아요 등록/취소 | 높음 | 서비스 크리티컬도 낮음 + Redis 캐시가 읽기 흡수 |
| created_at 관련 2개 | INSERT/DELETE | 낮음 | 불변값 |
| price 관련 2개 | 가격 수정 (관리자) | 낮음 | 변경 빈도 낮아 허용 |

### 포기한 것

필터 조건이 하나 추가될 때마다 인덱스 조합이 배로 늘어난다. 현재 `brandId × 정렬(3)`으로도 6개인데, 카테고리 필터가 추가되면 수십 개가 필요해진다.

**필터가 늘어날 때의 전환 전략**

임계점에 도달했을 때 두 방향을 고려할 수 있다.

**방향 1 — DB 내에서 버티기 (필터 3~4개 수준)**

조합별 복합 인덱스 대신 정렬 인덱스만 유지하고, 나머지 필터는 WHERE 조건으로만 처리한다.

```sql
-- idx_products_likes_desc 하나로 커버
SELECT ... FROM products
WHERE deleted_at IS NULL AND brand_id = ? AND category_id = ?
ORDER BY like_count DESC
LIMIT 20
```

인덱스 수가 정렬 종류(3개)로 고정되어 필터가 늘어도 인덱스를 추가할 필요가 없다. 대신 브랜드·카테고리 조합에서 rows가 늘어나는데, Redis 캐시가 읽기를 흡수하는 구조라면 캐시 미스 시의 비용만 감수하면 된다.

**방향 2 — 검색 계층 분리 (필터가 그 이상으로 늘어날 때)**

Elasticsearch를 도입해 검색·필터·정렬을 위임하고, DB는 원본 저장 전용으로만 쓴다.

```
쓰기: DB 저장 → ES 동기화 (이벤트 or 배치)
읽기: 목록 조회 → ES → 결과 반환
```

필터 조합이 아무리 복잡해져도 인덱스를 추가할 필요 없고, 역인덱스 구조라 다중 필터에 유연하게 대응한다. 대신 DB-ES 동기화 지연과 운영 복잡도가 증가한다.

**전환 타이밍**

필터 수보다 **텍스트 검색 요구사항이 들어오는 순간**이 더 자연스러운 트리거라고 생각한다. DB 인덱스로는 텍스트 검색을 구조적으로 감당하기 어렵기 때문이다. 그 시점에 ES를 도입하면서 필터·정렬도 함께 위임하는 것이 한 번의 전환으로 두 문제를 해결하는 방법이다.

---

## Future Work

OFFSET 페이지네이션의 딥 페이지 문제는 인덱스로 해결할 수 없다. 커버링 인덱스로 row lookup을 줄여도 OFFSET 50,000 기준 여전히 50,020번 스캔이고, 이는 1페이지 20번 대비 2,500배다.

구조적 해결 방향은 OFFSET 자체를 없애는 것이라고 생각한다. 커서 기반 페이지네이션(`WHERE id < :lastId LIMIT 20`)이 그 방법 중 하나인데, 페이지 깊이와 무관하게 항상 rows = 20으로 고정된다. 다만 정렬 기준이 `like_count`처럼 중복 가능한 컬럼이면 커서 설계가 복잡해지므로, 도입 시 정렬 조건별 커서 전략을 별도로 고민해야 할 것 같다.

---

