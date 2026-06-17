-- Stage 1 베이스라인 실행계획. 현재 코드(스칼라 서브쿼리 기반 like 카운트)와 동일 구조.
-- 시나리오 S1~S4 정의는 ../../reports/00-setup.md §6.
--   EXPLAIN          : 옵티마이저 추정(비실행) — 모든 시나리오에서 항상 가능.
--   EXPLAIN ANALYZE  : 실제 실행 후 실측 — 단건 30초 초과면 DNF 처리(돌리지 않음).
-- S1·S2(LIKES_DESC)는 ORDER BY 상관 서브쿼리 탓에 실행 시 DNF(>30s) → EXPLAIN(추정)만.
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < 01-explain-baseline.sql
SET @brand_id := 847;          -- 인기 브랜드 (1,134개 상품, sanity 3)
SET @hot_product_id := 45577;  -- 좋아요 Top 상품 (5,000 likes, sanity 7)

-- ============ S1·S2 : LIKES_DESC — 실행 시 DNF(>30s), EXPLAIN(추정)만 ============

-- S1 · 좋아요순 · 전역 · 1페이지
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) DESC, p.id DESC
LIMIT 20 OFFSET 0;

-- S2 · 좋아요순 · 인기 브랜드 · 1페이지
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = @brand_id
ORDER BY (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) DESC, p.id DESC
LIMIT 20 OFFSET 0;

-- ============ S3·S4 : 실측 가능 (정렬에 상관 서브쿼리 없음) — EXPLAIN + EXPLAIN ANALYZE ============

-- S3 · 최신순 · 전역 · 1페이지 (createdAt DESC, id DESC)
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20 OFFSET 0;

-- S4 · 상세 · 단건
EXPLAIN
SELECT p.id, p.name, p.description, b.id, b.name, p.price, p.stock,
       (SELECT COUNT(l.id) FROM likes l WHERE l.product_id = p.id) AS like_count
FROM products p
JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.id = @hot_product_id AND p.deleted_at IS NULL;

-- 실측이 필요한 S3·S4 는 위 EXPLAIN 을 EXPLAIN ANALYZE FORMAT=TREE 로 바꿔 한 번 더 실행한다.
