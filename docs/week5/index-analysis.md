# 5주차 — 상품 인덱스 분석 (EXPLAIN 전/후)

## 1. 측정 환경

- DB: MySQL 8.x (로컬 docker 또는 호스트 설치)
- 데이터: `product` 10만 건, `brand` 50건
- 시드: `apps/commerce-api/src/main/resources/db/seed/seed-products-100k.sql`
- 통계 갱신: `ANALYZE TABLE product;` (시드 마지막 단계에 포함)

## 2. 대상 유즈케이스

상품 목록 API 의 4가지 정렬/필터 조합을 측정 대상으로 한다.

| ID | 유즈케이스 | 쿼리 패턴 |
|----|----------|---------|
| Q1 | 브랜드 필터 + 좋아요순 | `WHERE brand_id=? ORDER BY like_count DESC` |
| Q2 | 전체 인기순 | `ORDER BY like_count DESC` |
| Q3 | 전체 최신순 | `ORDER BY created_at DESC` |
| Q4 | 가격 오름차순 | `ORDER BY price ASC` |

> 페이지네이션은 5주차 범위가 아니므로 우선 `LIMIT 20` 으로 통일해 측정.

## 3. 인덱스 설계

`ProductModel` 의 `@Index` 로 선언 (Hibernate ddl-auto=create 시 CREATE INDEX 자동 발행).

| 인덱스 | 컬럼 | 노리는 쿼리 | 트레이드오프 |
|--------|------|------------|------------|
| `idx_product_brand_likes` | `(brand_id, like_count DESC)` | Q1 (커버링 정렬) | 좋아요 토글 시 인덱스 페이지 갱신 비용 |
| `idx_product_likes` | `(like_count DESC)` | Q2 | 위와 동일 — 정렬용으로만 사용 |
| `idx_product_created` | `(created_at DESC)` | Q3 | INSERT 시 인덱스 끝 페이지 갱신 (hot-spot 우려 낮음) |
| `idx_product_price` | `(price)` | Q4 + 가격 범위 필터 (장래) | 가격 수정 빈도 낮아 비용 ↓ |

### 왜 `(brand_id, like_count DESC)` 인가?

- B+Tree 의 좌측 일치 원칙(leftmost prefix) 상, `WHERE brand_id=? ORDER BY like_count DESC` 는 이 복합 인덱스 하나로 **레인지 스캔 → 정렬 회피** 가 가능하다.
- 단일 `(brand_id)` + 단일 `(like_count)` 두 개로 분리하면 옵티마이저가 하나만 선택해 나머지는 `filesort` 가 발생한다.
- DESC 방향까지 명시한 이유: MySQL 8.0+ 는 descending index 를 실제로 지원한다 (8.0 이전엔 무시되고 ASC 와 동일). 8.0 환경 기준 인덱스 정렬과 ORDER BY 정렬이 일치하므로 `Backward index scan` 도 회피된다.

### 왜 좋아요 + 브랜드 묶음만 복합으로 하고, 나머지는 단일 인덱스인가?

- "브랜드 필터 + 좋아요순" 은 실측 유스케이스의 핵심 — 인기 페이지에서 가장 자주 등장한다.
- 나머지 정렬(`최신순`, `가격순`) 은 브랜드 필터와 묶이는 빈도가 낮다고 가정 — 비용 대비 효과 낮다.
- 추후 트래픽이 늘어 "브랜드 + 최신순" 이 핫해지면 `(brand_id, created_at DESC)` 추가를 고려.

## 4. 측정 절차

```bash
# 1) 앱 한 번 띄워서 스키마 생성 (Hibernate ddl-auto=create)
./gradlew :apps:commerce-api:bootRun  # Ctrl+C 로 종료해도 무방

# 2) 시드 적재 (~ 수십 초)
mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers \
  < apps/commerce-api/src/main/resources/db/seed/seed-products-100k.sql

# 3) EXPLAIN 측정 — 아래 쿼리를 인덱스 전/후로 비교
mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers
```

### Q1 — 브랜드 필터 + 좋아요순

```sql
EXPLAIN FORMAT=TREE
SELECT id, name, like_count
  FROM product
 WHERE brand_id = 3
 ORDER BY like_count DESC
 LIMIT 20;
```

**인덱스 추가 전 (시뮬레이션)**
- type: `ref` (brand_id FK 자동 인덱스가 잡혀 있을 수도) 또는 `ALL`
- Extra: `Using where; Using filesort`
- 예상 rows: brand_id 분포에 따라 1,000 ~ 5,000

**인덱스 추가 후 (`idx_product_brand_likes`)**
- type: `ref`
- Extra: `Using index condition`  (filesort 제거)
- 예상 rows: LIMIT 만큼만 빠르게 추출

### Q2 — 전체 인기순

```sql
EXPLAIN FORMAT=TREE
SELECT id, name, like_count
  FROM product
 ORDER BY like_count DESC
 LIMIT 20;
```

**인덱스 추가 전**: `type=ALL`, `Extra=Using filesort` — 10만 행 전체 정렬.
**인덱스 추가 후 (`idx_product_likes`)**: `type=index`, `Extra=` (filesort 없음).

### Q3 — 전체 최신순

```sql
EXPLAIN FORMAT=TREE
SELECT id, name, created_at
  FROM product
 ORDER BY created_at DESC
 LIMIT 20;
```

`idx_product_created` 로 동일 패턴.

### Q4 — 가격 오름차순

```sql
EXPLAIN FORMAT=TREE
SELECT id, name, price
  FROM product
 ORDER BY price ASC
 LIMIT 20;
```

`idx_product_price` 활용.

## 5. 측정 결과 기록 (실측 채워넣을 표)

| 쿼리 | 인덱스 전 (ms / rows / Extra) | 인덱스 후 (ms / rows / Extra) | 개선 배율 |
|------|----------------------|----------------------|---------|
| Q1 | TODO | TODO | TODO |
| Q2 | TODO | TODO | TODO |
| Q3 | TODO | TODO | TODO |
| Q4 | TODO | TODO | TODO |

> 로컬 측정 후 위 표를 실제 EXPLAIN 출력으로 갱신할 것. 측정은 `SET profiling=1; SELECT ...; SHOW PROFILES;` 또는 `EXPLAIN ANALYZE` (MySQL 8.0.18+) 활용.

## 6. 주의사항

- **분포 편향**: 시드 데이터는 `brand_id` 가 작은 번호에 편향되어 있다. brand_id=1 (가장 많은 상품) 으로 측정하면 인덱스 효과가 작게 보일 수 있음 — 옵티마이저가 PK 풀스캔이 더 싸다고 판단할 수 있기 때문. 중간 분포(brand_id ≈ 25) 와 함께 측정 권장.
- **버퍼풀 캐시 효과**: 동일 쿼리를 반복 실행하면 두 번째부터 InnoDB buffer pool 에 캐시되어 빨라진다. 인덱스 전/후 비교는 각각 처음 실행 결과로 한다.
- **Hibernate ddl-auto=create**: 앱 재시작 시 스키마/데이터가 모두 날아간다. 측정 중에는 앱을 다시 띄우지 말 것. 또는 `ddl-auto=validate` 로 임시 전환.
