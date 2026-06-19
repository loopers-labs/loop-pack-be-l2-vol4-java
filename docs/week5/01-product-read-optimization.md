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
| 상품 상세 | `product:detail:{productId}` | 10분 | 상품 수정/삭제, 좋아요 등록/취소 |
| 상품 목록 | `product:list:brand:{brandId}:sort:{sort}:page:{page}:size:{size}` | 30초 | 상품 생성/수정/삭제, 좋아요 등록/취소 |

캐시 miss 또는 Redis 장애 시에는 DB 조회 경로로 fallback한다.

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

## 판단 근거

- Materialized View는 이번 과제에서 Nice-To-Have다. 현재 구현은 이미 `Product.likeCount` 비정규화 구조를 갖고 있으므로, 먼저 이 구조를 인덱스와 캐시로 보강한다.
- 상품 상세 캐시는 키가 단순해서 명시 무효화가 가능하다.
- 상품 목록 캐시는 조건 조합이 많고 좋아요 변경에 영향을 받으므로 TTL을 짧게 둔다. 현재 구현은 변경 이벤트에서 추적 중인 목록 키를 지우지만, 운영 규모가 커지면 짧은 TTL 중심으로 무효화 범위를 줄이는 선택도 가능하다.
- Redis 장애나 캐시 miss는 서비스 실패가 아니라 DB 조회 fallback으로 처리한다.
