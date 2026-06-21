# 06. Performance Improvement — 성능 개선 설계 및 진행 기록

> Round 5 요구사항: 실제 트래픽에서 자주 발생하는 조회 병목 문제를 구조적으로 해결한다.
> 인덱스 / 반정규화 / 캐시를 활용해 상품 목록·상세 조회 성능을 개선한다.

---

## 요구사항 목록

### Must-Have

| # | 항목 | 상태 |
|---|------|------|
| ① | 상품 목록 조회 — 브랜드 필터 + 좋아요순 정렬 인덱스 최적화 | ✅ 완료 |
| ② | 좋아요 수 정렬 구조 개선 — 반정규화(`like_count`) 또는 Materialized View | ✅ 완료 |
| ③ | 상품 목록·상세 API Redis 캐시 적용 | ✅ 완료 |
| ④ | 목록 조회 DTO 분리 및 JPQL Projection 적용 | ✅ 완료 |

### Nice-To-Have

| # | 항목 | 상태 |
|---|------|------|
| - | Materialized View | ⬜ |

---

## ① 상품 목록 조회 인덱스 최적화

### 실행 쿼리 구조

정렬 조건은 `Pageable`에 `Sort`를 담아 전달하고, 필터 조합(brandId, inStock)에 따라 4개 메서드로 분기한다.

| brandId | inStock | 실행 메서드 |
|---------|---------|-----------|
| null | false | `findAllActive(pageable)` |
| 있음 | false | `findAllActiveByBrandId(brandId, pageable)` |
| null | true | `findAllActiveInStock(pageable)` — EXISTS 서브쿼리 |
| 있음 | true | `findAllActiveByBrandIdInStock(brandId, pageable)` — EXISTS + brandId |

`ORDER BY`는 정렬 조건마다 Pageable이 동적으로 붙여주므로 메서드를 추가로 분리하지 않는다.

### 고민했던 것들

#### OR 조건 문제 → 쿼리 분리

초기 구현:
```sql
WHERE deleted_at IS NULL AND (:brandId IS NULL OR brand_id = :brandId)
```
`OR :brandId IS NULL` 조건이 있으면 옵티마이저가 "brandId가 null일 수도 있다"고 판단해
인덱스를 타지 않고 풀스캔을 선택한다. 브랜드 필터가 있든 없든 항상 풀스캔 발생.

**해결:** `findAllActive` / `findAllActiveByBrandId` 두 메서드로 분리.
호출부(`ProductRepositoryImpl`)에서 `brandId == null` 여부로 분기.

#### deleted_at IS NULL을 인덱스에 포함하지 않은 이유

`deleted_at IS NULL`은 삭제되지 않은 상품(= 거의 전체)에 해당하므로 selectivity가 매우 낮다.
이 컬럼을 인덱스 선두에 두면 오히려 인덱스 효율이 떨어진다.
정렬 조건(`like_count`, `created_at`, `price`, `brand_id`)이 이미 높은 selectivity를 가지므로 제외.

#### deleted_at IS NULL selectivity가 낮아도 LIMIT이 있으면 인덱스가 효과적인 이유

초기에는 "PRICE_ASC / LATEST 정렬은 `deleted_at IS NULL` selectivity 문제로 인덱스 불필요"라고 판단했다.
그러나 실제 EXPLAIN 결과, `LIMIT 20`이 함께 있으면 `type: index`(인덱스 순서 스캔)로 동작해 rows가 99,449 → **20**으로 줄었다.

이유: 인덱스 스캔 방식은 인덱스를 정렬 순서대로 읽으면서 `deleted_at IS NULL` 조건에 맞는 행을 20개 찾는 즉시 멈춘다.
selectivity가 낮아도 LIMIT이 조기 종료 조건으로 작동하므로 실질 읽기 비용은 작다.

**결론:** `deleted_at IS NULL` selectivity 문제는 LIMIT 없는 전체 스캔에서만 인덱스를 무력화한다.
LIMIT이 있는 페이지네이션 쿼리에서는 정렬 인덱스가 충분히 효과적이다.

#### like_count 인덱스 유지 결정 — Redis 캐시와의 조합

`like_count`는 좋아요 등록/취소마다 UPDATE되는 컬럼이라 인덱스 쓰기 비용이 존재한다.

**검토한 우려 사항:**

좋아요 1회 → `idx_products_likes_desc` B-Tree 갱신 + `idx_products_brand_likes` B-Tree 갱신

이론상 인기 상품에 좋아요가 폭발적으로 몰리면 같은 인덱스 페이지 lock 경합이 발생할 수 있다.

**유지로 결론 내린 이유:**

첫째, 좋아요는 이 서비스에서 크리티컬한 기능이 아니다. 정렬 기준으로 쓰이지만 결제나 재고처럼 정합성이 엄격하게 요구되지 않는다. 설계 당시부터 "대략적인 인기순"을 의도했고, lock 경합이 생길 정도의 트래픽은 현재 서비스 규모에서 현실적이지 않다.

둘째, Redis 캐시(TTL 5분)가 읽기 경로를 흡수한다. 캐시 히트 시 DB 인덱스는 전혀 사용되지 않는다. 캐시 미스는 5분마다 한 번 발생하는데, 이때 인덱스가 없으면 filesort(99K rows)가 실행된다. 인덱스가 있으면 rows 20으로 끝난다.

```
읽기 경로: 캐시 히트 → DB 미접근 (인덱스 무관)
           캐시 미스 → 인덱스 스캔 rows 20  vs  filesort rows 99K
쓰기 경로: 항상 row UPDATE + 인덱스 갱신 (Redis 캐시와 무관)
```

셋째, 쓰기 경합의 실질적 원인은 인덱스가 아니라 `UPDATE products SET like_count = like_count ± 1` 자체의 row lock이다. 극단적 트래픽에서는 인덱스 갱신 몇 마이크로초보다 row lock 대기가 병목이 된다. 인덱스 제거로 해결되는 문제가 아니다.

**검토했지만 선택하지 않은 방향 — Redis Sorted Set(ZSET):**

```
좋아요 등록 → ZADD products:ranking {like_count} {productId}
좋아요순 조회 → ZREVRANGE products:ranking 0 19
```

쓰기 경합은 해소되지만 브랜드 필터·inStock 필터와 조합할 때 브랜드별 ZSET 관리, cold start 초기화, 필터 조합별 중간 연산 등 구현 복잡도가 크게 늘어난다. 현재 서비스 규모에서는 오버엔지니어링이라 판단했다.

#### price 인덱스 쓰기 비용 트레이드오프

`price`는 상품 수정 시 변경되는 컬럼이다. `idx_products_price`와 `idx_products_brand_price` 2개 인덱스가 갱신된다.

`like_count`와 달리 상품 가격 변경은 관리자가 의도적으로 수행하는 작업이므로 빈도가 낮다.
쓰기 비용보다 가격순 조회 성능 개선 효과가 크다고 판단해 인덱스를 포함했다.

#### like_count 정합성 — Eventual Consistency 허용

`likes` 테이블(원본)과 `products.like_count`(반정규화)가 동기화 중 불일치할 수 있다.
원자 UPDATE(`like_count = like_count ± 1`)로 처리하지만, 장애 상황에서는 차이가 생길 수 있다.

**판단:** 좋아요 수 정렬은 "대략적인 인기순"이 목적이므로 강정합성 불필요.
일시적 불일치는 서비스에 큰 영향 없음 → Eventual Consistency 허용.

#### brand_id nullable 유지 결정

노브랜드 상품의 `brand_id`를 null로 둘지, "노브랜드" 전용 브랜드 레코드로 둘지 검토했다.

**Option A — 현행 유지 (nullable)**
- 노브랜드 상품 → `brand_id = NULL`
- 브랜드 필터 조회: `WHERE brand_id = ?` / 전체 조회: OR 조건 발생
- 단점: OR 조건으로 인해 옵티마이저가 인덱스를 타지 않음

**Option B — Null Object Pattern (sentinel 값)**
- 노브랜드 전용 브랜드 레코드를 하나 만들고 (`brands` 테이블에 "노브랜드" 행 삽입)
- 노브랜드 상품 → `brand_id = {노브랜드 id}`
- 브랜드 필터 조회: 항상 `WHERE brand_id = ?` 단일 조건 → OR 조건 불필요

**판단 — Option A 유지**

쿼리를 `findAllActive` / `findAllActiveByBrandId`로 분리하면서 OR 조건 문제는 이미 해결됐다.
브랜드 필터가 없는 경우 OR 조건 자체가 존재하지 않으므로 Option B로 데이터 모델을 바꿀 이유가 없어졌다.

Option B는 "브랜드 없음"이라는 의미를 코드와 DB 양쪽에서 별도로 관리해야 하는 복잡도가 생기므로,
쿼리 분리만으로 해결된 현 시점에서는 오버엔지니어링이다.

#### 인덱스 조합 폭발 경고

현재 필터는 `brandId`, `inStock` 2개, 정렬은 3가지다. 필터 조건이 하나 추가될 때마다 인덱스 조합이 배로 늘어난다.

예를 들어 카테고리 필터가 추가되면 `브랜드 × 카테고리 × 정렬(3)` 조합으로 이론상 수십 개의 인덱스가 필요해진다.
이 시점부터는 인덱스 전략보다 **검색 전용 저장소(Elasticsearch 등)** 도입을 검토해야 한다.

### 인덱스 설계

실제 적용한 인덱스:

| 인덱스명 | 컬럼 | 커버 케이스 |
|----------|------|------------|
| `idx_products_likes_desc` | `(like_count DESC, created_at DESC)` | 전체 좋아요순 |
| `idx_products_brand_likes` | `(brand_id, like_count DESC, created_at DESC)` | 브랜드 + 좋아요순 |
| `idx_products_created_at` | `(created_at DESC)` | 전체 최신순 |
| `idx_products_brand_created_at` | `(brand_id, created_at DESC)` | 브랜드 + 최신순 |
| `idx_products_price` | `(price ASC)` | 전체 가격순 |
| `idx_products_brand_price` | `(brand_id, price ASC)` | 브랜드 + 가격순 |

**컬럼별 인덱스 적용 기준:**

| 컬럼 | 변경 빈도 | 인덱스 여부 | 이유 |
|------|----------|------------|------|
| `created_at` | 없음 (INSERT 시 1회) | ✅ | 불변값, 쓰기 비용 없음 |
| `price` | 낮음 (관리자만) | ✅ | 변경 빈도 낮아 쓰기 비용 허용 |
| `like_count` | 높음 (사용자 행동마다) | ✅ | 서비스 크리티컬도 낮음 + Redis 캐시가 읽기 흡수 → 쓰기 비용 허용 |

### 인덱스 추가 방법

`ddl-auto: create` 환경이므로 `@Table(indexes = ...)` 어노테이션으로 관리.
재시작 시 스키마 재생성 → 인덱스 자동 생성 → `ProductDataInitializer`로 데이터 재투입.

### EXPLAIN 검증 흐름

인덱스 설계는 아래 3단계를 거쳐 최종 결론에 도달했다.

**1단계 — 인덱스 없음, 좋아요순 EXPLAIN 확인**

요구사항인 좋아요순 정렬부터 분석했다.

| 케이스 | type | key | rows | filesort |
|--------|------|-----|------|---------|
| 전체 좋아요순 | `ALL` | NULL | 99,516 | ✅ |
| 브랜드 + 좋아요순 | `ALL` | NULL | 99,516 | ✅ |

풀스캔 + filesort. 인덱스 없이 10만 건을 전부 읽고 정렬.

---

**2단계 — like_count 인덱스 추가** (`idx_products_likes_desc`, `idx_products_brand_likes`)

| 케이스 | type | key | rows | filesort |
|--------|------|-----|------|---------|
| 전체 좋아요순 | `index` | idx_products_likes_desc | **20** | ❌ 제거 |
| 브랜드 + 좋아요순 | `ref` | idx_products_brand_likes | **9,116** | ❌ 제거 |

좋아요순 정렬 해결. 이 시점에서 최신순·가격순 정렬도 같은 문제가 있을지 추가로 검토.

---

**3단계 — 최신/가격순 EXPLAIN 확인 후 인덱스 추가** (최종)

최신순·가격순도 EXPLAIN을 돌려보니 동일하게 풀스캔 + filesort 발생.
created_at·price 인덱스를 추가해 모든 정렬 케이스를 커버.
이 과정에서 like_count 인덱스의 쓰기 비용을 재검토했고, 유지로 최종 결론 (근거는 위 섹션 참고).

| 케이스 | type | key | rows | filesort |
|--------|------|-----|------|---------|
| 전체 좋아요순 | `index` | idx_products_likes_desc | **20** | ❌ 제거 |
| 전체 최신순 | `index` | idx_products_created_at | **20** | ❌ 제거 |
| 전체 가격순 | `index` | idx_products_price | **20** | ❌ 제거 |
| 브랜드 + 좋아요순 | `ref` | idx_products_brand_likes | **9,116** | ❌ 제거 |
| 브랜드 + 최신순 | `ref` | idx_products_brand_created_at | **5,032** | ❌ 제거 |
| 브랜드 + 가격순 | `ref` | idx_products_brand_price | **5,032** | ❌ 제거 |

### 현재 인덱스별 쓰기 비용 정리

| 인덱스 | 갱신 발생 시점 | 빈도 |
|--------|--------------|------|
| `idx_products_likes_desc` | INSERT, DELETE, like_count 변경 | 높음 (허용) |
| `idx_products_brand_likes` | INSERT, DELETE, like_count 변경 | 높음 (허용) |
| `idx_products_created_at` | INSERT, DELETE | 낮음 |
| `idx_products_brand_created_at` | INSERT, DELETE | 낮음 |
| `idx_products_price` | INSERT, DELETE, price 변경 | 낮음 |
| `idx_products_brand_price` | INSERT, DELETE, price 변경 | 낮음 |

### 확장 시나리오 — 재고 필터 추가 시

#### stocks.product_id 인덱스 누락

상품 목록 조회 시 `stocks` IN 쿼리(`WHERE product_id IN (...)`)가 실행되는데,
`product_id`에 인덱스가 없어 매번 10만 건 풀스캔이 발생하고 있었다.
→ `StockModel`에 `idx_stocks_product_id` unique 인덱스 추가로 해결.

#### "재고 있는 상품만 보기" 필터를 JOIN으로 추가하면 안 되는 이유

```sql
-- 단순 JOIN 추가 시
SELECT p.* FROM products p
JOIN stocks s ON s.product_id = p.id
WHERE p.deleted_at IS NULL
  AND (s.total_stock - s.reserved_stock) > 0
ORDER BY p.like_count DESC, p.created_at DESC
LIMIT 20
```

옵티마이저가 재고 필터의 selectivity를 보고 `stocks`를 드라이빙 테이블로 선택할 수 있다.
그러면 `products`의 정렬 인덱스가 무력화되고 filesort 재발.

products 인덱스(정렬 드라이빙)와 재고 필터(stocks 드라이빙)가 충돌하는 구조.

#### 선택지 비교

| 방법 | 설명 | 판단 |
|------|------|------|
| JOIN | 옵티마이저 재량에 따라 인덱스 무력화 가능 | ❌ |
| 반정규화 (`available_stock`) | JOIN 불필요, 빠름. 단, 재고는 정합성이 중요한 데이터 → 쓰기마다 동기화 부담 | ❌ 정합성 리스크 |
| EXISTS 서브쿼리 | products 인덱스 드라이빙 유지. 재고 소진 상품이 많을수록 느려짐 | ✅ 기능 정확성 보장 |
| Redis 캐시 | EXISTS의 실행 빈도를 줄임. 캐시 히트 시 쿼리 자체 미실행 | ✅ EXISTS와 함께 적용 |

**결론:** EXISTS 쿼리로 기능을 정확하게 구현하고, Redis 캐시로 실행 빈도를 줄인다.
Redis 캐시는 느린 쿼리의 실행 횟수를 줄이는 것이지, 쿼리 자체를 빠르게 하는 게 아님.
캐시 미스 시 EXISTS가 실행되므로 둘은 택일이 아닌 조합이다.

#### EXISTS 쿼리 구조

```sql
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
  AND EXISTS (
      SELECT 1 FROM stocks s
      WHERE s.product_id = p.id
        AND s.total_stock - s.reserved_stock > 0
  )
ORDER BY p.like_count DESC, p.created_at DESC
LIMIT 20
```

products 인덱스 순서대로 읽으면서 재고 조건 확인 → LIMIT 채우면 즉시 멈춤.
재고 소진 상품이 폭발적으로 많아지면 EXISTS도 한계에 도달하지만,
그 시점에는 Redis 캐시 히트율이 충분히 커버한다.

#### 실제 구현 내용

**API 변경:**
```
GET /api/v1/products?sortBy=likes_desc&inStock=true
```
`inStock` 쿼리 파라미터 추가 (기본값 `false` → 기존 동작 유지).

쿼리 분기 구조는 상단 [실행 쿼리 구조] 표 참고. 총 4개 쿼리 메서드로 분기하며, 각 쿼리가 독립적으로 최적 인덱스를 활용한다.

### 데이터 분포와 옵티마이저 선택

테스트 데이터 분포를 달리하면서 EXPLAIN 결과를 비교했다.

| 케이스 | 20브랜드 균등 1M | 100브랜드 비균등 1M |
|--------|:---------------:|:------------------:|
| 전체 좋아요순 | index / 20 | index / 20 |
| 전체 최신순 | index / 20 | index / 20 |
| 전체 가격순 | index / 20 | index / 20 |
| 브랜드 + 좋아요순 | ref / 93,736 | ref / 18,916 |
| 브랜드 + 최신순 | index / 211 | index / 1,050 |
| 브랜드 + 가격순 | ref / 93,736 | ref / 18,916 |

**브랜드 + 최신순만 다른 이유:**

20브랜드 균등 분포에서는 created_at 단일 인덱스 스캔 시 brand_id=1이 1/20 확률로 고르게 분포해 rows=211로 추정. 옵티마이저가 composite ref (93K)보다 저렴하다고 판단.

100브랜드 비균등 분포에서는 brand_id=1이 가장 오래된 브랜드로 최근 구간에서 희박 (filtered=0.19%). rows=1,050으로 증가. 그러나 여전히 composite ref (18,916)보다 낮아 index 스캔 선택.

**결론:** 테스트 데이터의 인위적 균등 분포가 만든 이상 현상. 실 운영에서 brand_id와 created_at 간 상관관계가 강해질수록 rows 추정이 높아지고, composite ref로 전환되는 임계점에 가까워진다. `idx_products_brand_created_at`이 필요한 이유.

### OFFSET 페이지네이션 한계 — 딥 페이지 문제

지금까지 EXPLAIN은 OFFSET 없는 1페이지 기준이었다. 실제 Pageable은 `LIMIT 20 OFFSET (page * 20)`을 생성한다.

**전체 최신순 — OFFSET에 정비례**

| OFFSET | rows |
|--------|------|
| 0 | 20 |
| 1,000 | 1,020 |
| 10,000 | 10,020 |
| 50,000 | 50,020 |

rows = OFFSET + LIMIT. 인덱스 스캔이 OFFSET만큼 더 내려가야 하므로 선형 증가.

**브랜드 + 최신순 — OFFSET에 따라 전략이 바뀜**

| OFFSET | type | key | rows | Extra |
|--------|------|-----|------|-------|
| 0 | index | idx_products_created_at | 1,050 | Using where |
| 100 | index | idx_products_created_at | 6,304 | Using where |
| 1,000 | ref | idx_products_brand_likes | 18,916 | Using where; **Using filesort** |

OFFSET 1,000에서 created_at 스캔을 포기하고 brand ref + filesort로 전환. brand_id=1을 1,020개 찾으려면 created_at 인덱스를 ~10만 행 스캔해야 하는데, 그것보다 brand ref로 18,916행을 가져와 정렬하는 쪽을 선택. filesort 재등장.

**핵심:** OFFSET이 커질수록 rows는 선형 증가, 깊은 페이지에서는 인덱스 전략 자체가 뒤집혀 filesort까지 부활한다. 이것이 offset 기반 페이지네이션의 구조적 한계다.

### 체크리스트

- [x] `ProductModel`에 `@Table(indexes = ...)` 추가
- [x] OR 조건 제거 → `findAllActive` / `findAllActiveByBrandId` 쿼리 분리
- [x] 앱 재시작 후 데이터 재생성 확인 (brands 20 / products 10만 / stocks 10만)
- [x] `StockModel`에 `idx_stocks_product_id` 인덱스 추가 (product_id 풀스캔 → 인덱스)
- [x] 재고 필터(`inStock`) EXISTS 쿼리 구현
- [x] 최신순·가격순 EXPLAIN 분석 → 인덱스 추가 후 비교 완료 (rows: 99,449 → 20 / filesort 제거)
- [x] like_count 인덱스 유지 결정 — 서비스 크리티컬도 낮음 + Redis 캐시 조합으로 쓰기 비용 허용
- [x] 1M건 EXPLAIN 비교 — 데이터 분포·OFFSET에 따른 옵티마이저 동작 차이 분석 완료
- [ ] 블로그용 AS-IS / TO-BE 비교 기록

---

## ② 좋아요 수 정렬 구조 개선 — 반정규화 (완료)

### 구조

- `likes` 테이블: 원본 데이터 — "누가 어떤 상품을 좋아했는지" 기록
- `products.like_count`: 반정규화된 집계값 — 좋아요 수를 products 테이블에 중복 저장

정규화된 구조라면 좋아요순 정렬 시 매번 `COUNT(*) FROM likes WHERE product_id = ?`를 10만 건에 수행해야 함.
`like_count`를 반정규화해 미리 집계해두고, 등록/취소 시 동기화하는 방식으로 해결.

### 동기화 처리 (LikeFacade)

```java
// 좋아요 등록
likeRepository.save(like);                        // likes 테이블에 기록
productRepository.incrementLikeCount(productId);  // like_count +1 (원자 UPDATE)

// 좋아요 취소
likeRepository.delete(like);                      // likes 테이블에서 삭제
productRepository.decrementLikeCount(productId);  // like_count -1 (원자 UPDATE)
```

원자 UPDATE(`UPDATE products SET like_count = like_count ± 1`)로 동시성 안전하게 처리.
(자세한 내용 → 05-architecture-decisions.md 결정 10)

---

## ③ Redis 캐시 적용

### 배경

EXISTS 서브쿼리와 인덱스로 쿼리 자체를 최적화했지만, 동일한 조건의 요청이 반복되면 매번 DB를 조회한다.
Redis 캐시는 쿼리 실행 자체를 생략해 DB 부하를 줄인다. 쿼리를 빠르게 하는 게 아니라, 실행 횟수를 줄이는 것.

### 대상 API 및 TTL 선택 이유

| API | 캐시 이름 | 캐시 키 | TTL | 이유 |
|-----|---------|---------|-----|------|
| `GET /api/v1/products/{id}` | `product` | `{productId}` | 10분 | 단건 조회는 키 충돌 없음, 상품 정보 변경 빈도 낮음 |
| `GET /api/v1/products` | `products` | `{sort}:{brandId}:{inStock}:{page}:{size}` | 5분 | 좋아요 변동이 잦아 stale 데이터 노출 시간을 짧게 유지 |

### 목록 캐시 hit rate 트레이드오프

목록 캐시는 sort × brandId × inStock × page × size 조합 수만큼 캐시 엔트리가 생긴다.
파라미터 조합이 다양할수록 동일한 키로 재요청이 들어올 확률(hit rate)이 낮아진다.
hit rate가 낮으면 메모리만 차지하고 DB 부하 감소 효과는 작다.

**그럼에도 적용하는 이유:**
메인 페이지처럼 `sort=latest&page=0` 같은 특정 조합에 트래픽이 집중되는 경우, 해당 조합의 hit rate가 높아진다.
실무에서는 모니터링으로 hit rate를 확인한 뒤 낮은 캐시는 걷어내는 방식으로 조정한다.

### 캐시 무효화(Evict) 전략

| 이벤트 | 무효화 대상 | 이유 |
|--------|-----------|------|
| 상품 수정 | `product:{id}` 단건 + `products` 전체 | 수정된 상품이 어느 페이지에 있는지 모름 |
| 상품 삭제 | `product:{id}` 단건 + `products` 전체 | 목록에서 제외되어야 함 |
| 좋아요 등록/취소 | `product:{id}` 단건 + `products` 전체 | `like_count`가 바뀌어 정렬 순서가 달라질 수 있음 |

목록 캐시는 어떤 파라미터 조합에 삭제 대상 상품이 포함되어 있는지 알 수 없으므로 `allEntries = true`로 전체 삭제한다.

### evict 타이밍

`@CacheEvict(beforeInvocation = false)` 기본값 사용 → 트랜잭션이 커밋된 후에 캐시를 삭제한다.
트랜잭션이 롤백되면 evict도 일어나지 않아, DB와 캐시 불일치를 방지한다.

### Redis 장애 대응 — CacheErrorHandler

Redis 서버 장애 시 `@Cacheable`, `@CacheEvict`에서 예외가 전파되면 서비스 전체가 중단된다.
`CacheErrorHandler`를 구현해 캐시 연산 실패를 삼키고, 장애 상황에서도 서비스가 동작하도록 처리했다.

```java
// CacheConfig.java
public class CacheConfig implements CachingConfigurer {
    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }
}
```

| 상황 | 동작 | 부작용 |
|------|------|--------|
| GET 실패 | warn 로그 + DB로 fallback | DB 부하 증가 (캐시 효과 없음) |
| PUT 실패 | warn 로그 + 캐싱 생략 | DB 결과는 정상 반환, 다음 요청도 DB 조회 |
| EVICT/CLEAR 실패 | warn 로그 + DB 업데이트는 완료 | Redis 복구 후 stale 데이터 TTL 만료 전까지 잔존 |

**stale 데이터 허용 판단:**
EVICT 실패 시 Redis 복구 후 기존 캐시 엔트리가 남아있어 수정 전 데이터가 노출될 수 있다.
TTL이 최대 10분이므로 최대 10분간 stale 상태가 유지된다.
상품 정보의 일시적 불일치는 서비스에 치명적이지 않으므로 허용했다.

### 구현 내용

**CacheConfig** (`config/CacheConfig.java`):
- `@EnableCaching`으로 Spring Cache 활성화
- `RedisCacheManager`에 캐시별 TTL 설정
- 키: `StringRedisSerializer`, 값: `GenericJackson2JsonRedisSerializer`
- `CachingConfigurer` 구현 → `RedisCacheErrorHandler` 등록

**ProductFacade:**
```java
@Cacheable(cacheNames = "product", key = "#productId")
public ProductInfo getProduct(Long productId) { ... }

@Cacheable(cacheNames = "products", key = "#sort.name() + ':' + #brandId + ':' + #inStock + ':' + #page + ':' + #size")
public List<ProductSummaryInfo> getProducts(...) { ... }

@Caching(evict = {
    @CacheEvict(cacheNames = "product", key = "#productId"),
    @CacheEvict(cacheNames = "products", allEntries = true)
})
public ProductInfo updateProduct(Long productId, ...) { ... }

@Caching(evict = {
    @CacheEvict(cacheNames = "product", key = "#productId"),
    @CacheEvict(cacheNames = "products", allEntries = true)
})
public void deleteProduct(Long productId) { ... }
```

**LikeFacade:**
```java
@Caching(evict = {
    @CacheEvict(cacheNames = "product", key = "#productId"),
    @CacheEvict(cacheNames = "products", allEntries = true)
})
public LikeInfo addLike(Long userId, Long productId) { ... }

@Caching(evict = {
    @CacheEvict(cacheNames = "product", key = "#productId"),
    @CacheEvict(cacheNames = "products", allEntries = true)
})
public void cancelLike(Long userId, Long productId) { ... }
```

### 체크리스트

- [x] `CacheConfig` — `@EnableCaching`, `RedisCacheManager`, TTL 설정
- [x] `ProductFacade.getProduct()` — `@Cacheable` 적용 (TTL 10분)
- [x] `ProductFacade.getProducts()` — `@Cacheable` 적용 (TTL 5분)
- [x] `ProductFacade.updateProduct()` / `deleteProduct()` — `@CacheEvict` 적용
- [x] `LikeFacade.addLike()` / `cancelLike()` — `@CacheEvict` 적용
- [x] `RedisCacheErrorHandler` — Redis 장애 시 예외 삼키고 DB fallback, warn 로그 기록

---

## ④ 목록 조회 DTO 분리 및 JPQL Projection 적용

### 배경

상품 목록 API(`GET /api/v1/products`) 응답에는 `description`이 포함되지 않는다.
그러나 기존 JPQL은 `SELECT p FROM ProductModel p`로 전체 엔티티를 조회해, 사용하지 않는 `description`까지 DB에서 읽어왔다.

DTO를 분리했으니 쿼리도 그에 맞게 맞추는 게 자연스럽다.

### 변경 내용

**타입 계층 분리:**

| 타입 | 용도 | description 포함 |
|------|------|:---:|
| `ProductInfo` | 상세 조회 전용 | ✅ |
| `ProductSummaryInfo` | 목록 조회 전용 | ❌ |
| `ProductSummaryModel` | JPQL projection용 record | ❌ |

`ProductSummaryModel`은 JPQL `SELECT NEW` 생성자 표현식에 사용되는 순수 데이터 컨테이너다.
`id, name, price, brandId, likeCount` 5개 컬럼만 가진다.

**JPQL 변경:**

```sql
-- 변경 전
SELECT p FROM ProductModel p WHERE p.deletedAt IS NULL

-- 변경 후
SELECT NEW com.loopers.product.domain.ProductSummaryModel(
    p.id, p.name, p.price, p.brandId, p.likeCount
) FROM ProductModel p WHERE p.deletedAt IS NULL
```

`ProductJpaRepository`의 4개 목록 쿼리(`findAllActive`, `findAllActiveByBrandId`, `findAllActiveInStock`, `findAllActiveByBrandIdInStock`) 모두 동일하게 적용.

### 커버링 인덱스 검토

Projection 적용 후 자연스럽게 떠오르는 다음 질문: "인덱스도 SELECT 컬럼에 맞게 바꾸면 row lookup 자체를 없앨 수 있지 않나?"

SELECT 컬럼별 인덱스 포함 여부:

| 컬럼 | 인덱스 포함 여부 |
|------|:---:|
| `id` | ✅ (PK, 보조 인덱스에 묵시적 포함) |
| `price` | ✅ |
| `brand_id` | ✅ |
| `like_count` | ✅ |
| **`name`** | ❌ |

`name`이 인덱스에 없어 row lookup이 반드시 발생한다. `name`을 6개 인덱스에 추가하면 varchar 컬럼으로 인해 인덱스 크기가 크게 늘고 쓰기 비용이 증가한다. 이득보다 비용이 크다.

**결론:** 인덱스는 변경하지 않는다. Projection 변경만으로도 row lookup 1회당 읽는 바이트가 줄어든다 (`description`이 크면 클수록 효과가 크다). 커버링 인덱스 적용은 현재 컬럼 구조상 현실적이지 않다.

### 커버링 인덱스가 가능했다면 얼마나 줄었을까

**1페이지(OFFSET 0) 기준: 거의 없다.**

LIMIT 20이 조기 종료 조건으로 작동해 row lookup이 20번뿐이다. 커버링 인덱스로 아껴도 20번이라 체감 차이가 없다.

**딥 페이지에서 커진다.**

OFFSET 50,000 + LIMIT 20 케이스:

| | 인덱스 스캔 | row lookup | 합계 |
|-|:---------:|:---------:|:----:|
| 커버링 인덱스 없음 | 50,020 | 50,020 | 100,040 |
| 커버링 인덱스 있음 | 50,020 | 0 | 50,020 |

row lookup이 전체 I/O의 50%를 차지하므로, OFFSET이 커질수록 커버링 인덱스 절감 효과도 50%에 수렴한다.

**그런데 아이러니가 있다.**

커버링 인덱스 효과가 가장 큰 구간이 offset 페이지네이션의 구조적 한계 구간과 정확히 겹친다. OFFSET 50,000은 커버링 인덱스로 줄여도 여전히 50,020번 스캔이고, 이건 1페이지 20번에 비해 2,500배 많다. 커버링 인덱스는 "느린 쿼리를 덜 느리게" 할 뿐, 딥 페이지 문제를 해결하지 못한다.

딥 페이지 문제의 실질적 해결책은 **커서 기반 페이지네이션** (`WHERE id < :lastId ORDER BY id DESC LIMIT 20`)으로 OFFSET 자체를 없애는 것이다. 이렇게 하면 페이지 깊이와 무관하게 항상 rows = 20으로 고정된다.

---

## 필요 테이블 및 데이터

### 관련 테이블

| 테이블 | 역할 | 인덱스 최적화 | 캐시 |
|--------|------|:---:|:---:|
| `products` | 핵심 조회 대상, like_count 반정규화 보유 | ✅ 필수 | ✅ 필수 |
| `brands` | 상품의 브랜드명 조회 | - | ✅ 목록 응답 포함 |
| `stocks` | 상품의 가용 재고 조회 | - | ✅ 목록 응답 포함 |
| `likes` | 좋아요 원본 데이터 (반정규화 소스) | - | - |

### 테스트 데이터 현황

두 가지 구성으로 나눠 테스트했다.

**소규모 (기본 개발·기능 검증용)**

| 테이블 | 건수 | 특이사항 |
|--------|------|---------|
| `brands` | 20개 | 브랜드-01 ~ 브랜드-20 |
| `products` | 100,000개 | brand_id 균등 분포 (브랜드당 약 5,000건), like_count 제곱 분포 (0~9,999), price 1,000~1,000,000원, created_at 최근 2년 균등 랜덤 |
| `stocks` | 100,000개 | product 1:1, total_stock 0~500 균등 분포 |

**대규모 (인덱스 성능·분포 영향 검증용)**

| 테이블 | 건수 | 특이사항 |
|--------|------|---------|
| `brands` | 100개 | 브랜드-01 ~ 브랜드-100 |
| `products` | 1,000,000개 | brand_id 균등 분포 (브랜드당 약 10,000건), like_count 제곱 분포 (0~9,999), price 1,000~1,000,000원, created_at 브랜드별 입점 시기 이후 비균등(최신 집중) 분포 |
| `stocks` | 1,000,000개 | product 1:1, total_stock 0~500 균등 분포 |

현재 로컬 기본값은 소규모(20브랜드 / 10만 건)다. 대규모 검증 시 `ProductDataInitializer` 상수를 변경해 재기동한다.

### 데이터 생성 방식

`support/init/ProductDataInitializer.java` — `@Profile("local")` `CommandLineRunner`
- `JdbcTemplate.batchUpdate()` 1,000건 단위 배치 INSERT
- 앱 시작 시 자동 실행, 데이터 있으면 skip
- 생성 소요 시간: 약 8~9초 (브랜드 + 상품 + 재고 합산)

