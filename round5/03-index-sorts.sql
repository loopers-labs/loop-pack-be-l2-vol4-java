-- =====================================================================
-- Round 5 ①  정렬 유즈케이스별 인덱스 (LATEST / PRICE_ASC)
--   - LIKES_DESC 는 02-index.sql 에서 이미 처리됨
--   - created_at: 불변 컬럼이라 인덱스 비용 저렴 + 기본 정렬이라 가치 큼
--   - price: 커머스 흔한 정렬, 변경은 드묾
-- =====================================================================

-- LATEST 정렬 (created_at DESC)
CREATE INDEX idx_product_brand_created ON product (brand_id, created_at);  -- 브랜드 + 최신
CREATE INDEX idx_product_created       ON product (created_at);            -- 전체 최신 (홈 기본)

-- PRICE_ASC 정렬 (price ASC)
CREATE INDEX idx_product_brand_price ON product (brand_id, price);         -- 브랜드 + 가격
CREATE INDEX idx_product_price       ON product (price);                   -- 전체 가격
