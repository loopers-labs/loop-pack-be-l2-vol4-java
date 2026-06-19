-- =====================================================================
-- Round 5 ①  LIKES_DESC 정렬 인덱스
--   - 브랜드 필터 + 좋아요순 / 전체 좋아요순
-- =====================================================================

CREATE INDEX idx_product_brand_like ON product (brand_id, like_count);  -- 브랜드 + 좋아요순
CREATE INDEX idx_product_like       ON product (like_count);            -- 전체 좋아요순
