-- =============================================================================
-- 06. DESC 인덱스로 교체 (ASC + backward scan ↔ DESC + forward scan 비교용)
-- =============================================================================
-- 04_indexes.sql 는 ASC 컬럼 인덱스를 만들었고, MySQL 은 ORDER BY likes_count DESC, id DESC
-- 를 "backward index scan (reverse)" 으로 처리했다.
--
-- 여기서는 정렬 컬럼을 명시적으로 DESC 로 선언한 인덱스로 교체해, MySQL 이 forward scan 으로
-- 같은 정렬을 충족하는지(그리고 타이밍 차이가 있는지) 비교한다.
--   - 데이터(120,000행)는 그대로 두고 인덱스만 교체한다.
--   - 선행 등치 컬럼(brand_id, deleted_at)의 방향은 무의미하므로 ASC 그대로 두고,
--     정렬에 쓰이는 likes_count, id 만 DESC 로 선언한다.
-- =============================================================================

USE loopers_bench;

-- ASC 인덱스 제거
DROP INDEX idx_brand_active_likes ON product;
DROP INDEX idx_active_likes       ON product;

-- DESC 인덱스 생성 (정렬 컬럼만 DESC)
CREATE INDEX idx_brand_active_likes_desc ON product (brand_id, deleted_at, likes_count DESC, id DESC);
CREATE INDEX idx_active_likes_desc       ON product (deleted_at, likes_count DESC, id DESC);

ANALYZE TABLE product;

SHOW INDEX FROM product;
