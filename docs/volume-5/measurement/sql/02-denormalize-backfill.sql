-- Stage 2 비정규화: products.like_count 컬럼 추가 + 기존 likes 기준 초기 백필.
-- 측정 DB(perf, ddl-auto:none)에서 1회 실행한다.
-- local/test(ddl-auto:create)는 엔티티 매핑이 컬럼을 생성하므로 이 스크립트가 필요 없다.
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < 02-denormalize-backfill.sql

-- 1) 비정규화 컬럼 추가 (신규 상품 기본값 0)
ALTER TABLE products ADD COLUMN like_count INT NOT NULL DEFAULT 0;

-- 2) 기존 likes 를 상품별로 한 번에 집계해 백필.
--    상관 서브쿼리(상품마다 likes 재스캔) 대신 GROUP BY 한 번으로 집계해 조인 — 대량에서 수십~수백 배 빠르다.
UPDATE products p
LEFT JOIN (
    SELECT product_id, COUNT(*) AS cnt
    FROM likes
    GROUP BY product_id
) agg ON agg.product_id = p.id
SET p.like_count = COALESCE(agg.cnt, 0);

-- 3) 검증 — 비정규화 값과 실제 집계가 일치해야 한다(불일치 행 0건 기대).
SELECT COUNT(*) AS mismatch
FROM products p
LEFT JOIN (
    SELECT product_id, COUNT(*) AS cnt FROM likes GROUP BY product_id
) agg ON agg.product_id = p.id
WHERE p.like_count <> COALESCE(agg.cnt, 0);
