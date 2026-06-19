# Round 5 — 조회 성능 개선 실험 자료

상품 목록/상세 조회의 **인덱스 · 비정규화 · 캐시** 개선을 측정한 스크립트 모음.

## 파일

| 파일 | 용도 |
|---|---|
| `01-seed.sql` | brand 100개 + product 100,000개 시딩 (분포 다양) |
| `02-index.sql` | LIKES_DESC 정렬 인덱스 |
| `03-index-sorts.sql` | LATEST / PRICE_ASC 정렬 인덱스 |
| `loadtest.js` | k6 부하 테스트 (목록 API, 동시 30명) |

## 사전 준비 (인프라 + 시딩)

```bash
# 1) 인프라 기동 (MySQL 3306, Redis 6379/6380)
docker compose -f docker/infra-compose.yml up -d mysql redis-master redis-readonly

# 2) 시딩 + 인덱스 (최초 1회. mysql-8-data 볼륨에 보존되므로 재기동해도 유지됨)
docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < round5/01-seed.sql
docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < round5/02-index.sql
docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < round5/03-index-sorts.sql
```

## EXPLAIN 측정

```sql
-- 인덱스 효과: type=ref / Using filesort 사라짐 확인
EXPLAIN ANALYZE
SELECT * FROM product WHERE brand_id=1 AND deleted_at IS NULL
ORDER BY like_count DESC LIMIT 20;
```

## 앱 기동 (주의: local 프로필은 ddl-auto=create 라 시딩이 날아감!)

```bash
# ddl-auto=none 으로 덮어써서 시딩 보존
JAVA_HOME=<java21-home> \
./gradlew :apps:commerce-api:bootRun --args='--spring.jpa.hibernate.ddl-auto=none'
```

## k6 부하 테스트

```bash
# 캐시 ON 측정 (정상)
docker exec -it redis-master redis-cli FLUSHALL
k6 run round5/loadtest.js

# 캐시 장애 테스트 (Redis 중단 → DB 폴백 확인)
docker stop redis-master redis-readonly
k6 run round5/loadtest.js          # commandTimeout 덕에 hang 없이 200 (느리지만 생존)
docker start redis-master redis-readonly
```

### 측정 결과 요약

| 상태 | p95 | 처리량 | 비고 |
|---|---|---|---|
| 캐시 ON | 3.7ms | 14,055 req/s | SLO(200ms) 통과 |
| Redis 중단 (commandTimeout 적용 후) | 655ms | 47 req/s | 전부 200, graceful degradation |

> 캐시가 DB 부하의 ~99.7%를 흡수 (14,055 → 47 req/s).
