# 06. Performance Improvement — 성능 개선 설계 및 진행 기록

> Round 5 요구사항: 실제 트래픽에서 자주 발생하는 조회 병목 문제를 구조적으로 해결한다.
> 인덱스 / 비정규화 / 캐시를 활용해 상품 목록·상세 조회 성능을 개선한다.

---

## 요구사항 목록

### Must-Have

| # | 항목 | 상태 |
|---|------|------|
| ① | 상품 목록 조회 — 브랜드 필터 + 좋아요순 정렬 인덱스 최적화 | ✅ 완료 |
| ② | 좋아요 수 정렬 구조 개선 — 비정규화(`like_count`) 또는 Materialized View | ✅ 완료 |
| ③ | 상품 목록·상세 API Redis 캐시 적용 | ⬜ |

### Nice-To-Have

| # | 항목 | 상태 |
|---|------|------|
| - | Materialized View | ⬜ |

---

## ① 상품 목록 조회 인덱스 최적화

### 대상 쿼리

```sql
-- ProductJpaRepository.findAllWithFilter
SELECT * FROM products
WHERE deleted_at IS NULL
  AND (brand_id = ? OR ? IS NULL)   -- 브랜드 필터 (선택)
ORDER BY like_count DESC, created_at DESC   -- LIKES_DESC
      -- price ASC                          -- PRICE_ASC
      -- created_at DESC                    -- LATEST
LIMIT ?, ?
```

### AS-IS 실행계획 (인덱스 없음)

```
type: ALL  /  key: NULL  /  rows: 99,516  /  Extra: Using where; Using filesort
```

- `type: ALL` → 풀 테이블 스캔 (10만 건 전부 읽음)
- `key: NULL` → 사용된 인덱스 없음
- `Using filesort` → 정렬을 메모리/디스크에서 별도 수행

브랜드 필터가 있어도 없어도 동일하게 풀스캔 + filesort 발생.

### 고민했던 것들

#### OR 조건 문제 → 쿼리 분리

기존 쿼리:
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
정렬 조건(`like_count`, `created_at`, `brand_id`)이 이미 높은 selectivity를 가지므로 제외.

#### like_count 인덱스 쓰기 비용 트레이드오프

`like_count`는 좋아요 등록/취소마다 UPDATE되는 컬럼이다.
인덱스가 있으면 매 UPDATE 시 인덱스도 갱신되므로 쓰기 비용이 증가한다.

"업데이트가 잦은 컬럼은 인덱스에 포함하지 않는다"는 원칙이 있지만, 이는 절대 법칙이 아니라 트레이드오프의 문제다.

**읽기 비용 vs 쓰기 비용 비교:**
- 상품 목록 조회: 매 페이지뷰마다 발생 → 매우 빈번
- 좋아요 등록/취소: 사용자가 의도적으로 누를 때만 → 조회 대비 훨씬 적음

조회 빈도 >> 쓰기 빈도인 일반적인 커머스 상황에서는 읽기를 빠르게 하기 위해 쓰기 비용을 감수하는 것이 합리적이다.

**언제 문제가 되는가:**
한정 세일·신상품 출시처럼 특정 상품에 좋아요가 폭발적으로 몰릴 때. 같은 row에 UPDATE가 집중되면서 row lock 경합이 발생하고, 인덱스 갱신까지 더해지면 쓰기 성능이 급격히 저하된다.

**그때의 개선 방향:**
- Redis에 like_count 카운터를 별도로 두고 배치로 MySQL 동기화 → 쓰기 경합 해소
- 조회는 Redis 캐시가 커버하므로 like_count 인덱스 의존도 낮아짐

**현재 판단:** 지금 트래픽 수준에서는 인덱스 포함이 적절. 좋아요 폭주 상황이 실제 문제가 될 때 배치 동기화로 전환한다.

#### like_count 정합성 — Eventual Consistency 허용

`likes` 테이블(원본)과 `products.like_count`(비정규화)가 동기화 중 불일치할 수 있다.
원자 UPDATE(`like_count = like_count ± 1`)로 처리하지만, 장애 상황에서는 차이가 생길 수 있다.

**판단:** 좋아요 수 정렬은 "대략적인 인기순"이 목적이므로 강정합성 불필요.
일시적 불일치는 서비스에 큰 영향 없음 → Eventual Consistency 허용.

#### brand_id nullable 유지 결정

노브랜드 상품의 `brand_id`를 null로 둘지, "노브랜드" 전용 브랜드 레코드로 둘지 검토했다.

**Option A — 현행 유지 (nullable)**
- 노브랜드 상품 → `brand_id = NULL`
- 브랜드 필터 조회: `WHERE brand_id = ?` / 전체 조회: `WHERE brand_id IS NULL OR brand_id = ?`
- 단점: OR 조건으로 인해 옵티마이저가 인덱스를 타지 않음

**Option B — Null Object Pattern (sentinel 값)**
- 노브랜드 전용 브랜드 레코드를 하나 만들고 (`brands` 테이블에 "노브랜드" 행 삽입)
- 노브랜드 상품 → `brand_id = {노브랜드 id}`
- 브랜드 필터 조회: 항상 `WHERE brand_id = ?` 단일 조건 → OR 조건 불필요
- 인덱스를 항상 탈 수 있음

**판단 — Option A 유지**

이번에 쿼리를 `findAllActive` / `findAllActiveByBrandId` 두 개로 분리하면서
OR 조건 문제는 이미 해결됐다. 브랜드 필터가 없는 경우 OR 조건 자체가 존재하지 않으므로
Option B로 데이터 모델을 바꿀 이유가 없어졌다.

Option B는 "브랜드 없음"이라는 의미를 코드와 DB 양쪽에서 별도로 관리해야 하는 복잡도가 생기므로,
쿼리 분리만으로 해결된 현 시점에서는 오버엔지니어링이다.

### 인덱스 설계

실제 적용한 인덱스:

| 인덱스명 | 컬럼 | 커버 케이스 |
|----------|------|------------|
| `idx_products_likes_desc` | `(like_count DESC, created_at DESC)` | 브랜드 필터 없이 전체 좋아요순 |
| `idx_products_brand_likes` | `(brand_id, like_count DESC, created_at DESC)` | 브랜드 필터 + 좋아요순 |

PRICE_ASC / LATEST 정렬은 `deleted_at IS NULL` selectivity 문제로 별도 인덱스 불필요.
현재 트래픽 기준 좋아요순이 핵심 케이스이므로 위 두 가지만 적용.

### 인덱스 추가 방법

`ddl-auto: create` 환경이므로 `@Table(indexes = ...)` 어노테이션으로 관리.
재시작 시 스키마 재생성 → 인덱스 자동 생성 → `ProductDataInitializer`로 데이터 재투입.

### TO-BE 실행계획 (인덱스 적용 후)

**브랜드 필터 없음 + 좋아요순:**
```
type: index  /  key: idx_products_likes_desc  /  rows: 20  /  Extra: Using where
```

**브랜드 필터 있음 + 좋아요순:**
```
type: ref  /  key: idx_products_brand_likes  /  rows: 9,116  /  Extra: Using where
```

### AS-IS vs TO-BE 비교

| 케이스 | AS-IS type | TO-BE type | AS-IS rows | TO-BE rows | filesort |
|--------|-----------|-----------|-----------|-----------|---------|
| 전체 좋아요순 | `ALL` | `index` | 99,516 | **20** | ❌ 제거 |
| 브랜드 + 좋아요순 | `ALL` | `ref` | 99,516 | **9,116** | ❌ 제거 |

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
그러면 `products`의 `idx_products_likes_desc` 인덱스가 무력화되고 filesort 재발.

좋아요순 인덱스(products 드라이빙)와 재고 필터(stocks 드라이빙)가 충돌하는 구조.

#### 선택지 비교

| 방법 | 설명 | 판단 |
|------|------|------|
| JOIN | 옵티마이저 재량에 따라 인덱스 무력화 가능 | ❌ |
| 비정규화 (`available_stock`) | JOIN 불필요, 빠름. 단, 재고는 정합성이 중요한 데이터 → 쓰기마다 동기화 부담 | ❌ 정합성 리스크 |
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

**쿼리 분기 구조 (`ProductRepositoryImpl.findAll`):**

| brandId | inStock | 실행 쿼리 |
|---------|---------|---------|
| null | false | `findAllActive` |
| 있음 | false | `findAllActiveByBrandId` |
| null | true | `findAllActiveInStock` (EXISTS) |
| 있음 | true | `findAllActiveByBrandIdInStock` (EXISTS + brandId) |

총 4개 쿼리 메서드로 분기. 각 쿼리가 독립적으로 최적 인덱스를 활용한다.

### 체크리스트

- [x] `ProductModel`에 `@Table(indexes = ...)` 추가
- [x] OR 조건 제거 → `findAllActive` / `findAllActiveByBrandId` 쿼리 분리
- [x] 앱 재시작 후 데이터 재생성 확인 (brands 20 / products 10만 / stocks 10만)
- [x] TO-BE EXPLAIN 분석 → AS-IS 비교 완료
- [x] `StockModel`에 `idx_stocks_product_id` 인덱스 추가 (product_id 풀스캔 → 인덱스)
- [x] 재고 필터(`inStock`) EXISTS 쿼리 구현
- [ ] 블로그용 AS-IS / TO-BE 비교 기록

---

## ② 좋아요 수 정렬 구조 개선 — 비정규화 (완료)

### 구조

- `likes` 테이블: 원본 데이터 — "누가 어떤 상품을 좋아했는지" 기록
- `products.like_count`: 비정규화된 집계값 — 좋아요 수를 products 테이블에 중복 저장

정규화된 구조라면 좋아요순 정렬 시 매번 `COUNT(*) FROM likes WHERE product_id = ?`를 10만 건에 수행해야 함.
`like_count`를 비정규화해 미리 집계해두고, 등록/취소 시 동기화하는 방식으로 해결.

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

### 대상 API

| API | 캐시 키 설계 (예시) | TTL |
|-----|-------------------|-----|
| `GET /api/v1/products/{id}` (상품 상세) | `product:{id}` | 10분 |
| `GET /api/v1/products` (상품 목록) | `products:sort={sort}&brandId={brandId}&page={page}&size={size}` | 5분 |

### 무효화 전략

| 이벤트 | 무효화 대상 |
|--------|------------|
| 상품 수정 (`PUT /products/{id}`) | `product:{id}` 단건 삭제 |
| 좋아요 등록/취소 | `product:{id}` 단건 삭제 + 목록 캐시 전체 삭제 |
| 상품 삭제 | `product:{id}` 단건 삭제 + 목록 캐시 전체 삭제 |

> 목록 캐시는 파라미터 조합이 많아 전체 삭제(`products:*` 패턴 삭제) 방식 사용.

### 체크리스트

- [ ] `ProductFacade.getProduct()` 캐시 적용
- [ ] `ProductFacade.getProducts()` 캐시 적용
- [ ] 상품 수정/삭제 시 캐시 무효화
- [ ] 좋아요 등록/취소 시 캐시 무효화
- [ ] 캐시 미스 시 정상 동작 확인 (fallback)
- [ ] TTL 설정 확인

---

## 필요 테이블 및 데이터

### 관련 테이블

| 테이블 | 역할 | 인덱스 최적화 | 캐시 |
|--------|------|:---:|:---:|
| `products` | 핵심 조회 대상, like_count 비정규화 보유 | ✅ 필수 | ✅ 필수 |
| `brands` | 상품의 브랜드명 조회 | - | ✅ 목록 응답 포함 |
| `stocks` | 상품의 가용 재고 조회 | - | ✅ 목록 응답 포함 |
| `likes` | 좋아요 원본 데이터 (비정규화 소스) | - | - |

### 테스트 데이터 현황

| 테이블 | 건수 | 특이사항 |
|--------|------|---------|
| `brands` | 20개 | 브랜드-01 ~ 브랜드-20 |
| `products` | 100,000개 | brand_id 균등 분포 (브랜드당 약 5,000건), like_count 제곱 분포 (0~9,999), price 1,000~1,000,000원, created_at 최근 2년 랜덤 |
| `stocks` | 100,000개 | product 1:1, total_stock 0~500 균등 분포 |

### 데이터 생성 방식

`support/init/ProductDataInitializer.java` — `@Profile("local")` `CommandLineRunner`
- `JdbcTemplate.batchUpdate()` 1,000건 단위 배치 INSERT
- 앱 시작 시 자동 실행, 데이터 있으면 skip
- 생성 소요 시간: 약 8~9초 (브랜드 + 상품 + 재고 합산)

---

## 현재 진행 상황

```
✅ 테스트 데이터 생성 (brands 20 / products 10만 / stocks 10만)
✅ AS-IS EXPLAIN 분석 확인 (풀스캔 + filesort)
✅ 인덱스 추가 → TO-BE EXPLAIN 비교 완료 (rows: 99,516 → 20 / filesort 제거)
✅ OR 조건 제거 → 쿼리 분리 (findAllActive / findAllActiveByBrandId)
⬜ Redis 캐시 적용
```
