# 상품 목록 조회 성능 측정

## 측정 환경

- 데이터: 상품 100,000건, 브랜드 10개
- DB: MySQL 8.0 (Docker)
- 측정 시나리오: 브랜드 필터(없음/있음) × 정렬 조건(LATEST/PRICE_ASC/LIKES_DESC) 조합 6가지

> 브랜드가 10개이므로 `brand_id` 필터 시 대상이 전체의 약 10%(1만건)로 줄어든다. 인덱스는 필터 효과와 `ORDER BY ... LIMIT 20` 정렬 최적화 두 가지 모두에서 이점을 제공한다.

---

## 유즈케이스 분류 및 인덱스 설계

| 시나리오 | WHERE 조건 | ORDER BY | 인덱스 |
|---|---|---|---|
| A1 | `deleted_at IS NULL` | `created_at DESC` | `(deleted_at, created_at)` |
| A2 | `deleted_at IS NULL` | `price ASC` | `(deleted_at, price)` |
| A3 | `deleted_at IS NULL` | `like_count DESC` | `(deleted_at, like_count)` |
| B1 | `brand_id = ? AND deleted_at IS NULL` | `created_at DESC` | `(brand_id, deleted_at, created_at)` |
| B2 | `brand_id = ? AND deleted_at IS NULL` | `price ASC` | `(brand_id, deleted_at, price)` |
| B3 | `brand_id = ? AND deleted_at IS NULL` | `like_count DESC` | `(brand_id, deleted_at, like_count)` |

**설계 원칙**: 등치(=) 또는 IS NULL 조건 컬럼을 앞에, 정렬 컬럼을 뒤에 배치하여 인덱스 자체가 정렬 순서를 갖도록 구성 → filesort 제거

---

## 1. 인덱스 적용 전 (AS-IS)

### EXPLAIN

6개 시나리오 모두 동일하게 `Table scan + filesort` 발생:

| 항목 | 값 |
|---|---|
| key | NULL |
| type | ALL |
| rows | 99,510 |
| Extra | Using where; Using filesort |

### API 응답 시간

| 시나리오 | 1회 | 2회 | 3회 | 평균 |
|---|---|---|---|---|
| A1 (전체 + LATEST) | 0.118s | 0.070s | 0.068s | 0.085s |
| A2 (전체 + PRICE_ASC) | 0.078s | 0.083s | 0.086s | 0.082s |
| A3 (전체 + LIKES_DESC) | 0.070s | 0.068s | 0.070s | 0.069s |
| B1 (브랜드 + LATEST) | 0.078s | 0.067s | 0.075s | 0.073s |
| B2 (브랜드 + PRICE_ASC) | 0.069s | 0.071s | 0.070s | 0.070s |
| B3 (브랜드 + LIKES_DESC) | 0.064s | 0.068s | 0.067s | 0.066s |

---

## 2. 인덱스 적용 후 (TO-BE)

### 추가한 인덱스

```sql
CREATE INDEX idx_product_deleted_at_created_at ON product (deleted_at, created_at);
CREATE INDEX idx_product_deleted_at_price       ON product (deleted_at, price);
CREATE INDEX idx_product_deleted_at_like_count  ON product (deleted_at, like_count);
CREATE INDEX idx_product_brand_id_deleted_at_created_at ON product (brand_id, deleted_at, created_at);
CREATE INDEX idx_product_brand_id_deleted_at_price      ON product (brand_id, deleted_at, price);
CREATE INDEX idx_product_brand_id_deleted_at_like_count ON product (brand_id, deleted_at, like_count);
```

### EXPLAIN

**A 시나리오 (브랜드 필터 없음)**

| 시나리오 | key | type | rows | Extra |
|---|---|---|---|---|
| A1 (LATEST) | idx_product_deleted_at_created_at | ref | 49,755 | Using where; Backward index scan |
| A2 (PRICE_ASC) | idx_product_deleted_at_price | ref | 49,755 | Using index condition |
| A3 (LIKES_DESC) | idx_product_deleted_at_like_count | ref | 49,755 | Using where; Backward index scan |

**B 시나리오 (브랜드 필터 있음)**

| 시나리오 | key | type | rows | filtered | Extra |
|---|---|---|---|---|---|
| B1 (LATEST) | idx_product_brand_id_deleted_at_created_at | ref | 19,526 | 100% | Using where; Backward index scan |
| B2 (PRICE_ASC) | idx_product_brand_id_deleted_at_price | ref | 19,526 | 100% | Using index condition |
| B3 (LIKES_DESC) | idx_product_brand_id_deleted_at_like_count | ref | 19,526 | 100% | Using where; Backward index scan |

### API 응답 시간

| 시나리오 | 1회 | 2회 | 3회 | 평균 |
|---|---|---|---|---|
| A1 (전체 + LATEST) | 0.066s | 0.036s | 0.036s | 0.046s |
| A2 (전체 + PRICE_ASC) | 0.054s | 0.035s | 0.034s | 0.041s |
| A3 (전체 + LIKES_DESC) | 0.069s | 0.062s | 0.036s | 0.056s |
| B1 (브랜드 + LATEST) | 0.087s | 0.027s | 0.029s | 0.048s |
| B2 (브랜드 + PRICE_ASC) | 0.027s | 0.027s | 0.027s | 0.027s |
| B3 (브랜드 + LIKES_DESC) | 0.027s | 0.026s | 0.026s | 0.026s |

---

## 3. 전후 비교 요약

| 시나리오 | AS-IS 평균 | TO-BE 평균 | 개선율 |
|---|---|---|---|
| A1 (전체 + LATEST) | 0.085s | 0.046s | 약 46% 단축 |
| A2 (전체 + PRICE_ASC) | 0.082s | 0.041s | 약 50% 단축 |
| A3 (전체 + LIKES_DESC) | 0.069s | 0.056s | 약 19% 단축 |
| B1 (브랜드 + LATEST) | 0.073s | 0.048s | 약 34% 단축 |
| B2 (브랜드 + PRICE_ASC) | 0.070s | 0.027s | 약 61% 단축 |
| B3 (브랜드 + LIKES_DESC) | 0.066s | 0.026s | 약 61% 단축 |

### 개선 포인트

- **AS-IS**: 6개 시나리오 전체 `type: ALL` (10만행 풀스캔) + `Using filesort`
- **TO-BE**: `type: ref` 로 인덱스 스캔, `Using filesort` 완전 제거. LIMIT 20건만 읽고 종료
- `Backward index scan`: DESC 정렬 시 MySQL 8.0이 인덱스를 역순으로 스캔하여 별도 정렬 없이 처리
- `Using index condition`: ICP(Index Condition Pushdown)로 스토리지 엔진 레벨에서 조건을 평가하여 불필요한 행 접근 최소화
