# 06. Performance Improvement — 성능 개선 설계 및 진행 기록

> Round 5 요구사항: 실제 트래픽에서 자주 발생하는 조회 병목 문제를 구조적으로 해결한다.
> 인덱스 / 비정규화 / 캐시를 활용해 상품 목록·상세 조회 성능을 개선한다.

---

## 요구사항 목록

### Must-Have

| # | 항목 | 상태 |
|---|------|------|
| ① | 상품 목록 조회 — 브랜드 필터 + 좋아요순 정렬 인덱스 최적화 | ⬜ |
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

### 필요 인덱스 설계

| 인덱스명 | 컬럼 | 커버 케이스 |
|----------|------|------------|
| `idx_products_likes_desc` | `(like_count DESC, created_at DESC)` | 브랜드 필터 없이 전체 좋아요순 |
| `idx_products_brand_likes` | `(brand_id, like_count DESC, created_at DESC)` | 브랜드 필터 + 좋아요순 |
| `idx_products_price_asc` | `(deleted_at, price ASC)` | 가격 오름차순 정렬 |
| `idx_products_latest` | `(deleted_at, created_at DESC)` | 최신순 정렬 |

> 04-erd.md의 인덱스 전략 참고 (이미 설계되어 있던 항목과 일치)

### 인덱스 추가 방법

`ddl-auto: create` 환경이므로 `@Table(indexes = ...)` 어노테이션으로 관리.
재시작 시 스키마 재생성 → 인덱스 자동 생성 → `ProductDataInitializer`로 데이터 재투입.

### 체크리스트

- [ ] `ProductModel`에 `@Table(indexes = ...)` 추가
- [ ] 앱 재시작 후 데이터 재생성 확인
- [ ] TO-BE EXPLAIN 분석 → AS-IS 비교
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
⬜ 인덱스 추가 → TO-BE EXPLAIN 비교
⬜ Redis 캐시 적용
```
