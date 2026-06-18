-- Stage 2 비정규화 실행계획. products.like_count 컬럼을 직접 조회/정렬(스칼라 서브쿼리 제거).
-- 시나리오 S1~S4 정의는 ../../reports/00-setup.md §6. 베이스라인 대비는 01-explain-baseline.sql.
-- 서브쿼리가 사라져 S1·S2(LIKES_DESC)도 실측 가능할 것으로 기대(베이스라인 DNF 해소 여부 확인).
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < 02-explain-denormalization.sql
SET @brand_id := 847;          -- 인기 브랜드 (1,134개 상품, sanity 3)
SET @hot_product_id := 45577;  -- 좋아요 Top 상품 (5,000 likes, sanity 7)

-- S1 · 좋아요순 · 전역 · 1페이지
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20 OFFSET 0;

-- S2 · 좋아요순 · 인기 브랜드 · 1페이지
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = @brand_id
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20 OFFSET 0;

-- S3 · 최신순 · 전역 · 1페이지 (created_at DESC, id DESC)
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20 OFFSET 0;

-- S4 · 상세 · 단건
EXPLAIN
SELECT p.id, p.name, p.description, b.id, b.name, p.price, p.stock, p.like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.id = @hot_product_id AND p.deleted_at IS NULL;

-- 실측이 필요한 시나리오는 위 EXPLAIN 을 02-analyze-denormalization.sql 에서 EXPLAIN ANALYZE FORMAT=TREE 로 실행한다.
