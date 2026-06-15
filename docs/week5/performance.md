# 상품 목록 조회 성능 측정

## 측정 환경

- 데이터: 상품 100,000건, 브랜드 10개
- DB: MySQL 8.0 (Docker)
- 측정 시나리오
  - A: 브랜드 필터 없음 + 좋아요 순 정렬
  - B: 브랜드 필터 있음 + 좋아요 순 정렬

---

## 1. 인덱스 적용 전 (AS-IS)

### EXPLAIN ANALYZE

MySQL 컨테이너에서 실행:
```bash
docker exec -it mysql mysql -uroot -proot loopers
```

**시나리오 A — 전체 + 좋아요 순 정렬**
```sql
EXPLAIN ANALYZE
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY like_count DESC
LIMIT 20;
```

```
-- 결과 붙여넣기
```

**시나리오 B — 브랜드 필터 + 좋아요 순 정렬**
```sql
EXPLAIN ANALYZE
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY like_count DESC
LIMIT 20;
```

```
-- 결과 붙여넣기
```

### API 응답 시간

앱 실행 후 아래 명령어로 측정 (3회 평균):
```bash
# 시나리오 A
curl -o /dev/null -s -w "time_total: %{time_total}s\n" \
  "http://localhost:8080/api/v1/products?sort=LIKES_DESC"

# 시나리오 B  (brandId=1 자리에 실제 브랜드 id 입력)
curl -o /dev/null -s -w "time_total: %{time_total}s\n" \
  "http://localhost:8080/api/v1/products?brandId=1&sort=LIKES_DESC"
```

| 시나리오 | 1회 | 2회 | 3회 | 평균 |
|---|---|---|---|---|
| A (전체 + 좋아요순) | - | - | - | - |
| B (브랜드 필터 + 좋아요순) | - | - | - | - |

---

## 2. 인덱스 적용 후 (TO-BE)

### 추가한 인덱스

```sql
-- 결과 붙여넣기
```

### EXPLAIN ANALYZE

**시나리오 A — 전체 + 좋아요 순 정렬**
```sql
EXPLAIN ANALYZE
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY like_count DESC
LIMIT 20;
```

```
-- 결과 붙여넣기
```

**시나리오 B — 브랜드 필터 + 좋아요 순 정렬**
```sql
EXPLAIN ANALYZE
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY like_count DESC
LIMIT 20;
```

```
-- 결과 붙여넣기
```

### API 응답 시간

| 시나리오 | 1회 | 2회 | 3회 | 평균 |
|---|---|---|---|---|
| A (전체 + 좋아요순) | - | - | - | - |
| B (브랜드 필터 + 좋아요순) | - | - | - | - |

---

## 3. 전후 비교 요약

| 시나리오 | AS-IS 평균 | TO-BE 평균 | 개선율 |
|---|---|---|---|
| A (전체 + 좋아요순) | - | - | - |
| B (브랜드 필터 + 좋아요순) | - | - | - |
