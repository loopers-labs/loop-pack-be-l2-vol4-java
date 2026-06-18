-- =====================================================================
-- Round 5 — 과제② 비정규화 like_count: 벤치 테이블 적용 + after 측정
-- =====================================================================
-- 1) products.like_count 컬럼 추가
-- 2) 기존 좋아요 100만을 집계해 1회 백필 (운영에선 등록/취소 시 원자 UPDATE 로 동기화)
-- 3) (brand_id, like_count) 인덱스
-- 4) likes_desc after 측정 — JOIN/GROUP BY/COUNT 사라지고 단일 테이블 정렬
-- =====================================================================

ALTER TABLE products ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;

-- 백필: 상품별 실제 좋아요 수로 채움 (1회성)
UPDATE products p
SET p.like_count = (SELECT COUNT(*) FROM likes l WHERE l.product_id = p.id);

CREATE INDEX idx_products_brand_likecount ON products (brand_id, like_count);

SHOW INDEX FROM products;

-- ---------------------------------------------------------------------
-- likes_desc AFTER : 브랜드 필터 + like_count 컬럼 정렬
-- 기대: type=ref, key=idx_products_brand_likecount, JOIN/temporary/filesort 소멸
-- ---------------------------------------------------------------------
EXPLAIN
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.like_count DESC
LIMIT 20 OFFSET 0;
