-- =====================================================================
-- Round 5 — TO-BE 인덱스 추가 (latest / price_asc 개선)
-- =====================================================================
-- latest:    brand_id 필터 + created_at DESC 정렬 → (brand_id, created_at)
-- price_asc: brand_id 필터 + price ASC 정렬      → (brand_id, price)
--
-- 복합 인덱스 "왼쪽→오른쪽" 규칙: 선두 컬럼(brand_id)으로 동등 필터 후,
-- 다음 컬럼(created_at / price)이 그 구간 안에서 이미 정렬돼 있어 filesort 가 사라진다.
--
-- likes_desc 는 COUNT 집계 정렬이라 인덱스로 못 잡음 → 과제 ②(like_count 비정규화)에서 처리.
-- =====================================================================

CREATE INDEX idx_products_brand_created ON products (brand_id, created_at);
CREATE INDEX idx_products_brand_price   ON products (brand_id, price);

SHOW INDEX FROM products;
