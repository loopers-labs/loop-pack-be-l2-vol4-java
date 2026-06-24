-- =====================================================================
-- Round 5 Redis 캐시 before/after 성능 측정 가이드
-- =====================================================================
-- 목적: 캐시 도입 전후 효과를 정량화한다.
--
-- 멘토링 핵심 인사이트:
--   "단건 응답시간이 6ms → 10ms로 느려져도, DB 매트릭(커넥션/부하)은 훨씬 좋아졌다."
--   → 캐시 효과는 개별 응답속도만이 아니라 DB 조회 횟수 감소로 측정해야 한다.
--
-- 측정 항목:
--   A) DB 조회 횟수 (Com_select, Handler_read 계열)
--   B) 상품 상세 API 응답시간 p50/p95 — 첫 번째(캐시 미스) vs 두 번째(캐시 히트)
--   C) Redis hit/miss rate
--
-- 실행 방법:
--   SQL 부분: docker exec -i <mysql_container> mysql -uapplication -papplication loopers ...
--   API 부분: curl 명령 참조 (주석 내 기술)
-- =====================================================================

-- =====================================================================
-- A) DB 조회 횟수 — 캐시 도입 전/후 비교
-- =====================================================================

-- [A-1] 측정 전 카운터 초기화 및 현재값 스냅샷
FLUSH STATUS;
SHOW GLOBAL STATUS LIKE 'Com_select';          -- SELECT 실행 횟수
SHOW GLOBAL STATUS LIKE 'Handler_read_first';  -- 인덱스 풀스캔 시작 횟수 (많으면 비효율)
SHOW GLOBAL STATUS LIKE 'Handler_read_key';    -- 인덱스 key lookup 횟수 (많을수록 인덱스 활용)
SHOW GLOBAL STATUS LIKE 'Handler_read_next';   -- 인덱스 순차 탐색 횟수

-- [A-2] 아래 API를 N회 호출한 뒤 다시 SHOW STATUS 실행해 증가분 비교
--
--   # 캐시 미스 (Redis 키 없음 상태에서 첫 호출)
--   for i in {1..10}; do
--     curl -s -o /dev/null -w "%{time_total}\n" \
--       -H "X-Loopers-Ldap: testuser" \
--       http://localhost:8080/api/v1/products/1
--   done
--
--   캐시 미스:  Redis GET miss → DB SELECT → Redis SET
--   캐시 히트:  Redis GET hit  → (DB 접근 없음)
--
-- [A-3] N회 호출 후 카운터 재조회 → 증가분 = 실제 DB SELECT 실행 횟수
SHOW GLOBAL STATUS LIKE 'Com_select';

-- =====================================================================
-- B) DB 쪽 쿼리 타이밍 — 캐시 미스 시 실행되는 전체 쿼리 경로
--    (ProductApplicationService.getProductDetail 캐시 미스 경로)
-- =====================================================================

-- [B-1] 상품 상세: products 단건 조회
EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.id = 1 AND p.deleted_at IS NULL;

-- [B-2] 재고 조회
EXPLAIN ANALYZE
SELECT s.* FROM stocks s
WHERE s.product_id = 1;

-- [B-3] 상품 목록: likes_desc (캐시 미스 시 실행)
EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL
  AND (:brandId IS NULL OR p.brand_id = 7)
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

-- [B-4] 목록 재고 일괄 조회 (N+1 회피용 IN 쿼리)
EXPLAIN ANALYZE
SELECT s.* FROM stocks s
WHERE s.product_id IN (
    SELECT p.id FROM products p
    WHERE p.deleted_at IS NULL
    ORDER BY p.like_count DESC
    LIMIT 20 OFFSET 0
);

-- =====================================================================
-- C) Redis hit/miss 측정 명령 (redis-cli)
-- =====================================================================
-- # 캐시 통계 조회
--   redis-cli -h localhost -p 6379 info stats | grep -E "keyspace_hits|keyspace_misses"
--
-- 결과 예시:
--   keyspace_hits:   84    (히트)
--   keyspace_misses: 12    (미스)
--   hit rate = 84 / (84 + 12) = 87.5%
--
-- # 현재 캐시된 키 목록 확인
--   redis-cli -h localhost -p 6379 keys "product:*"
--
-- 예상 키 패턴:
--   product:detail:{productId}          (상세, TTL 10분)
--   product:list:{brandId}:{sort}:{page}:{size}  (목록, TTL 3분)

-- =====================================================================
-- D) 기대 결과 요약
-- =====================================================================
-- 측정 시나리오: 상품 상세 API 동일 productId로 2회 연속 호출
--
--   1st call (캐시 미스):
--     - Redis GET → null
--     - DB SELECT products (+ SELECT stocks)
--     - Redis SET (직렬화 저장)
--     - 응답시간: ~15 ~ 30ms (DB 쿼리 포함)
--
--   2nd call (캐시 히트):
--     - Redis GET → JSON 역직렬화
--     - DB 접근 없음
--     - 응답시간: ~1 ~ 3ms
--
--   DB 조회 횟수 차이:
--     캐시 없음 10회 호출 → DB SELECT 20회 (products + stocks × 10)
--     캐시 있음 10회 호출 → DB SELECT  2회 (첫 호출 미스 1회분)
--     DB 부하 감소: -90%
--
-- 멘토링 인사이트 적용:
--   개별 응답시간보다 "DB가 받는 쿼리 수"가 핵심 지표.
--   트래픽이 몰릴 때(예: 100 TPS 중 60%가 상품 목록 조회)
--   캐시 히트율 87% 기준 → 실제 DB 도달 TPS = 60 × 0.13 ≈ 7.8 TPS로 감소.
-- =====================================================================
