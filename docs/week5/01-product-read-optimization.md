# Round 5 상품 조회 성능 개선

## TL;DR

상품 목록의 `brandId + likes_desc` 조회는 `Product.likeCount` 비정규화 값을 유지하고, 복합 인덱스와 Redis cache-aside 전략을 함께 적용한다.

## 범위

- 상품 목록 조회: `brandId`, `sort`, `page`, `size`
- 상품 상세 조회: `productId`
- 좋아요 등록/취소: `Product.likeCount` 동기화와 캐시 무효화

## AS-IS

- 상품 목록 조회는 JPA `PageRequest`와 `Sort`로 처리한다.
- `likes_desc` 정렬은 `Product.likeCount` 기준으로 동작한다.
- 좋아요 등록/취소 시 `Product`를 비관적 락으로 조회하고 `likeCount`를 증감한다.
- Redis 설정은 있었지만 상품 조회 캐시는 적용되어 있지 않았다.

## TO-BE

### Index

상품 목록에서 자주 쓰는 `brandId + 정렬` 조합을 기준으로 복합 인덱스를 추가했다.

| 유스케이스 | 인덱스 |
| --- | --- |
| 브랜드 필터 + 좋아요순 | `idx_product_brand_like_count (brand_id, like_count)` |
| 브랜드 필터 + 낮은 가격순 | `idx_product_brand_price (brand_id, price)` |
| 브랜드 필터 + 최신순 | `idx_product_brand_created_at (brand_id, created_at)` |

### Structure

좋아요 관계 원본은 `product_like` 테이블에 유지한다.
정렬용 집계 값은 `product.like_count`에 둔다.

이 선택은 조회 시 `JOIN + GROUP BY + ORDER BY`를 매번 수행하지 않기 위한 비정규화다.
대신 좋아요 등록/취소 시 `likeCount` 동기화 책임이 생긴다.

### Cache

Redis cache-aside 전략을 적용했다.

| 대상 | 키 | TTL | 무효화 |
| --- | --- | --- | --- |
| 상품 상세 | `product:detail:{productId}` | 10분 | 상품 수정/삭제, 브랜드 삭제, 좋아요 등록/취소 |
| 상품 목록 | `product:list:brand:{brandId}:sort:{sort}:page:{page}:size:{size}` | 30초 | 상품 생성/수정/삭제, 브랜드 삭제, 좋아요 등록/취소 |

캐시 miss 또는 Redis 장애 시에는 DB 조회 경로로 fallback한다.
캐시 직렬화 실패도 본 조회 흐름을 실패시키지 않고 캐시 쓰기만 건너뛴다.
목록 캐시 키 추적 set은 목록 캐시 TTL보다 조금 길게 유지해, 목록 키가 만료된 뒤 추적 set만 장기간 남는 상황을 줄인다.

## Benchmark 절차

로컬 MySQL을 실행한다.

```bash
docker-compose -f ./docker/infra-compose.yml up -d mysql redis-master redis-readonly
```

애플리케이션을 한 번 실행해 Hibernate schema를 생성한다.

```bash
./gradlew :apps:commerce-api:bootRun
```

10만 건 상품 데이터를 준비하고 `EXPLAIN`을 확인한다.

```bash
mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers \
  < docs/week5/sql/product-read-optimization-benchmark.sql
```

## EXPLAIN 기록

측정 환경:

- MySQL 8.0 Docker container
- 상품 100,000건
- 브랜드 100건
- 브랜드별 상품 약 1,000건

| 조회 | 상태 | key | type | rows | Extra |
| --- | --- | --- | --- | --- | --- |
| `brandId + likes_desc` | AS-IS | `NULL` | `ALL` | 99,450 | `Using where; Using filesort` |
| `brandId + likes_desc` | TO-BE | `idx_product_brand_like_count` | `ref` | 1,000 | `Using where; Backward index scan` |
| `brandId + price_asc` | AS-IS | `NULL` | `ALL` | 99,450 | `Using where; Using filesort` |
| `brandId + price_asc` | TO-BE | `idx_product_brand_price` | `ref` | 1,000 | `Using where` |
| `brandId + latest` | AS-IS | `NULL` | `ALL` | 99,450 | `Using where; Using filesort` |
| `brandId + latest` | TO-BE | `idx_product_brand_created_at` | `ref` | 1,000 | `Using where; Backward index scan` |

## EXPLAIN ANALYZE 기록

| 조회 | 상태 | 실행 요약 |
| --- | --- | --- |
| `brandId + likes_desc` | AS-IS | table scan 100,000 rows, sort, actual time 약 30.1ms |
| `brandId + likes_desc` | TO-BE | index lookup 20 rows, actual time 약 0.159ms |
| `brandId + price_asc` | AS-IS | table scan 100,000 rows, sort, actual time 약 27.2ms |
| `brandId + price_asc` | TO-BE | index lookup 20 rows, actual time 약 0.099ms |
| `brandId + latest` | AS-IS | table scan 100,000 rows, sort, actual time 약 23.4ms |
| `brandId + latest` | TO-BE | index lookup 20 rows, actual time 약 0.077ms |

## Cache / HTTP 부하 측정

캐시는 DB 실행 계획이 없으므로 `EXPLAIN`이 아니라 HTTP 응답 시간, 처리량, Redis/MySQL 카운터, Actuator 지표로 확인했다.

측정 조건:

- 실행 방식: `./gradlew :apps:commerce-api:bootRun`
- 데이터: 상품 100,000건, 브랜드 100건
- 대상 API: `GET /api/v1/products?brandId=42&sort=likes_desc&page=0&size=20`
- 부하 도구: ApacheBench `ab -n 1000 -c 20`
- 모니터링: `/actuator/prometheus`, Redis `INFO stats`, MySQL `SHOW GLOBAL STATUS`

### Cold Cache

Redis를 매 요청 전에 `FLUSHALL`하고 30회 단건 요청을 측정했다.

| count | min | avg | p50 | p95 | max |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 30 | 8.45ms | 20.27ms | 9.77ms | 24.24ms | 297.09ms |

`max`는 로컬 환경의 일시적 outlier로 보고, 비교 판단은 p50/p95를 우선 본다.

### Warm Cache

Redis를 비운 뒤 1회 prewarm 요청으로 목록 캐시를 생성하고, 같은 API에 `ab -n 1000 -c 20`을 실행했다.

| 지표 | 결과 |
| --- | ---: |
| Complete requests | 1,000 |
| Failed requests | 0 |
| Requests per second | 2,561.72 req/s |
| Time per request | 7.807ms |
| p50 | 6ms |
| p95 | 14ms |
| p99 | 29ms |
| Actuator server-side avg | 6.023ms |

### Cache Hit / DB 부하 확인

Warm cache 부하 구간의 카운터 변화:

| 카운터 | 변화 |
| --- | ---: |
| Redis `keyspace_hits` | +1,000 |
| Redis `keyspace_misses` | +0 |
| MySQL `Com_select` | +5 |

초기 측정에서는 Redis hit가 1,000건 발생해도 MySQL `Com_select`가 약 1,000건 증가했다.
원인은 상품 목록 캐시 확인 전에 `brandService.validateBrandExists(brandId)`를 매번 호출하던 흐름이었다.
캐시 hit 시에는 캐시된 목록 자체가 이미 유효한 브랜드 조건으로 만들어진 결과이므로, 캐시를 먼저 확인하고 miss일 때만 브랜드 존재 검증과 DB 조회를 수행하도록 보강했다.

보강 후에는 warm cache 요청 1,000건에서 Redis hit가 1,000건 발생했고 MySQL select 증가는 5건 수준으로 줄었다.
잔여 select는 측정/상태 확인 과정과 커넥션 관리에서 발생할 수 있는 로컬 환경 노이즈로 본다.

### 서버 상태 스냅샷

Warm cache 부하 직후 Actuator/Prometheus 스냅샷:

| 지표 | 값 |
| --- | ---: |
| JVM heap used | 약 80.7MB |
| JVM heap committed | 약 174.0MB |
| JVM live threads | 64 |
| Hikari active / idle / pending | 0 / 30 / 0 |
| process CPU usage | 약 25.8% |
| system CPU usage | 약 68.7% |

## 판단 근거

- Materialized View는 이번 과제에서 Nice-To-Have다. 현재 구현은 이미 `Product.likeCount` 비정규화 구조를 갖고 있으므로, 먼저 이 구조를 인덱스와 캐시로 보강한다.
- 상품 상세 캐시는 키가 단순해서 명시 무효화가 가능하다.
- 상품 목록 캐시는 조건 조합이 많고 좋아요 변경에 영향을 받으므로 TTL을 짧게 둔다. 현재 구현은 변경 이벤트에서 추적 중인 목록 키를 지우지만, 운영 규모가 커지면 짧은 TTL 중심으로 무효화 범위를 줄이는 선택도 가능하다.
- Redis 장애, 캐시 miss, 캐시 직렬화 실패는 서비스 실패가 아니라 DB 조회 fallback 또는 캐시 쓰기 skip으로 처리한다.
- 상품/브랜드 삭제, 좋아요 변경 시 캐시는 명시적으로 무효화한다. 다만 Redis 장애로 무효화가 실패하더라도 DB 변경을 실패시키지는 않고, 짧은 목록 TTL로 stale 가능 시간을 제한한다.
- 로컬 측정에서는 인덱스 적용 후 DB 조회 자체도 충분히 빨라, 캐시는 단건 latency보다 반복 조회의 DB 부하 흡수와 p95 안정화 측면에서 의미가 컸다.
