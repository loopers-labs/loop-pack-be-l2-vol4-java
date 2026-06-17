-- ============================================================================
-- 5주차 읽기 최적화 — 인덱스 EXPLAIN 측정용 시드 (10만 상품)
-- ============================================================================
-- 사용법 (로컬 MySQL):
--   1) 앱을 한 번 띄워 Hibernate ddl-auto=create 로 스키마를 만든다
--   2) mysql -h localhost -P 3306 -u application -papplication loopers \
--        < apps/commerce-api/src/main/resources/db/seed/seed-products-100k.sql
--   3) ANALYZE TABLE product;
--   4) docs/week5/index-analysis.md 의 EXPLAIN 쿼리 실행 → 결과 기록
-- ============================================================================

-- MySQL 의 재귀 CTE 기본 깊이 제한(1000) 을 풀어준다
SET SESSION cte_max_recursion_depth = 1000000;

-- 1) 브랜드 50개 (다양한 분포 확보)
INSERT INTO brand (created_at, updated_at, name, description)
WITH RECURSIVE seq(n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50
)
SELECT NOW(), NOW(),
       CONCAT('브랜드_', LPAD(n, 3, '0')),
       CONCAT('테스트 브랜드 ', n)
FROM seq;

-- 2) 상품 10만 (brand_id / price / like_count / created_at 분포 다양화)
--    - brand_id: 50개에 비균등 분포 (좌-편향: 작은 brand_id 에 더 많이 — 실제 인기 브랜드 시뮬레이션)
--    - like_count: 0~99999 멱법칙 유사 (대부분 낮고, 일부 high) — like 정렬 인덱스 검증용
--    - price: 1000~9_999_000
--    - created_at: 최근 365일 사이 분산
INSERT INTO product (
    created_at, updated_at, brand_id, name, description,
    price, stock, like_count, image_url, status
)
WITH RECURSIVE seq(n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND(n) * 365 * 24 * 60) MINUTE) AS created_at,
    NOW() AS updated_at,
    -- 좌-편향 brand_id: 작은 번호일수록 자주 등장. 1 ~ 50.
    GREATEST(1, LEAST(50, FLOOR(1 + ABS(RAND(n * 7)) * RAND(n * 13) * 50))) AS brand_id,
    CONCAT('상품_', LPAD(n, 6, '0')) AS name,
    CONCAT('상품 설명 ', n) AS description,
    1000 + FLOOR(RAND(n * 17) * 9_998_000) AS price,
    FLOOR(RAND(n * 19) * 1000) AS stock,
    -- like_count 멱법칙: RAND^4 → 대부분 0~10, 일부 수만
    FLOOR(POW(RAND(n * 23), 4) * 100000) AS like_count,
    NULL AS image_url,
    'ACTIVE' AS status
FROM seq;

-- 3) 옵티마이저 통계 갱신 — EXPLAIN 결과가 정확해진다
ANALYZE TABLE product;
ANALYZE TABLE brand;
