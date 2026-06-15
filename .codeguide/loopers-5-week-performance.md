# Round 5 Performance Notes

## Scope

5주차 조회 최적화 대상은 공개 상품 목록과 상품 상세 API다.

- 상품 목록: `GET /api/v1/products?brandId&sort&page&size`
- 상품 상세: `GET /api/v1/products/{productId}`
- 주요 정렬: `likes_desc`
- 주요 필터: `status = ON_SALE`, optional `brandId`

## 선택한 구조

| 항목 | 선택 |
| --- | --- |
| 좋아요 수 정렬 | `product.like_count` 비정규화 |
| 좋아요 수 동기화 | 좋아요 등록/취소 트랜잭션 안에서 `product.like_count` 증감 |
| 상품 목록 인덱스 | `status, brand_id, like_count` 중심 복합 인덱스 |
| 상품 목록 캐시 | Redis string cache, key 추적 set 기반 목록 무효화 |
| 상품 상세 캐시 | Redis string cache, 상품 ID 단위 무효화 |
| TTL | `commerce.cache.product.ttl=5m` |

## Cache Key

| 대상 | 형식 |
| --- | --- |
| 상품 상세 | `commerce:product:detail:v1:{productId}` |
| 상품 목록 | `commerce:product:list:v1:status:{status}:brand:{brandId|all}:sort:{sort}:page:{page}:size:{size}` |
| 목록 키 set | `commerce:product:list:v1:keys` |

사용자별 `liked` 값은 캐시에 저장하지 않는다.
캐시에는 상품, 브랜드, 좋아요 수 기반의 공개 조회 결과만 저장하고, 응답 직전에 요청 사용자 기준 좋아요 여부를 다시 계산한다.

## Invalidation

| 이벤트 | 무효화 |
| --- | --- |
| 상품 등록 | 상품 목록 캐시 전체 무효화 |
| 상품 수정/판매중지 | 해당 상품 상세 캐시, 상품 목록 캐시 전체 무효화 |
| 브랜드 수정 | 해당 브랜드 상품 상세 캐시, 상품 목록 캐시 전체 무효화 |
| 브랜드 삭제 | 해당 브랜드 상품 상세 캐시, 상품 목록 캐시 전체 무효화 |
| 좋아요 등록/취소 | 해당 상품 상세 캐시, 상품 목록 캐시 전체 무효화 |

목록 캐시는 Redis `KEYS`를 쓰지 않고, 캐시 저장 시 목록 키를 set에 기록한 뒤 무효화 시 set members만 삭제한다.

## EXPLAIN 기준

로컬 성능 검증 SQL은 `.codeguide/loopers-5-week-performance.sql`에 둔다.

검증 순서:

1. 격리된 로컬 MySQL schema에서 10만 건 이상 상품 데이터를 준비한다.
2. 인덱스 적용 전 `EXPLAIN ANALYZE` 결과를 저장한다.
3. `idx_product_status_brand_like` 등 TO-BE 인덱스를 적용한다.
4. 동일 쿼리의 `EXPLAIN ANALYZE` 결과를 다시 저장한다.
5. `type`, `key`, `rows`, `Extra`, actual time을 기준으로 전후를 비교한다.

## Trade-off

- `like_count` 비정규화는 조회와 정렬 비용을 줄이지만 좋아요 쓰기 시 상품 row 갱신 비용이 추가된다.
- 강한 정합성을 위해 좋아요 이력 변경과 카운터 변경을 같은 DB 트랜잭션에서 처리한다.
- Redis 목록 캐시는 페이지/정렬/브랜드 조건별 키가 늘어날 수 있다.
- 좋아요 변경 시 목록 캐시 전체를 무효화해 정합성을 우선한다. 트래픽이 더 커지면 브랜드 단위 목록 키 set 분리를 검토한다.
- Redis 장애는 조회 실패로 전파하지 않고 DB 조회로 fallback한다.
