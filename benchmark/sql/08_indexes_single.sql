-- =============================================================================
-- 08. 단일 컬럼 인덱스로 교체 (복합 인덱스와 대비용)
-- =============================================================================
-- 가설: "등치 필터 + ORDER BY 를 모두 인덱스로 충족(filesort 제거)하려면 필터/정렬 컬럼이 한
--        복합 인덱스에 함께 있어야 한다. 단일 컬럼 인덱스로는 구조적으로 불가."
-- → 단일 인덱스 3개(brand_id / likes_count / deleted_at)만 두고, 옵티마이저가 무엇을 고르는지,
--   filesort 가 남는지, examined rows 가 얼마인지 확인한다.
--
-- 데이터(120,000행)는 그대로 두고 인덱스만 교체한다.
-- =============================================================================

USE loopers_bench;

DROP INDEX idx_brand_active_likes_desc ON product;
DROP INDEX idx_active_likes_desc       ON product;

CREATE INDEX idx_brand_id    ON product (brand_id);
CREATE INDEX idx_likes_count ON product (likes_count);
CREATE INDEX idx_deleted_at  ON product (deleted_at);

ANALYZE TABLE product;

SHOW INDEX FROM product;
