-- =====================================================================
-- Round 5 — AS-IS (인덱스 추가 전) 측정 스크립트
-- =====================================================================
-- 사용법:
--   docker exec -i <mysql_container> mysql -uapplication -papplication loopers < sql/round5/explain_round5_before.sql
-- 또는 MySQL 콘솔에 한 블록씩 붙여 실행하며 결과를 캡처한다.
--
-- 측정 지표:
--   - EXPLAIN          : type(ALL=풀스캔), key(쓰인 인덱스, NULL=미사용), rows(스캔 예상), Extra(filesort/temporary)
--   - EXPLAIN ANALYZE  : actual time=... (추정이 아닌 실측 실행 시간)
-- 비교 변수 통제: 동일 brand_id(=7), 동일 LIMIT/OFFSET. 인덱스 유무만 바꿔 before/after 비교.
-- 캐시 영향: MySQL 8 에는 쿼리 캐시가 없으나 버퍼 풀은 남으므로,
--           각 쿼리를 2~3회 실행해 첫 회(콜드)는 버리고 안정된 값으로 기록한다.
-- =====================================================================

-- 현재 인덱스 상태 확인 (AS-IS: products 는 PRIMARY 뿐, likes 는 UK 뿐)
SHOW INDEX FROM products;
SHOW INDEX FROM likes;


-- =====================================================================
-- ① latest : 브랜드 필터 + 최신순
-- 예상: type=ALL(또는 brand 필터만 부분), Extra=Using filesort
-- =====================================================================
EXPLAIN
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.created_at DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.created_at DESC
LIMIT 20 OFFSET 0;


-- =====================================================================
-- ② price_asc : 브랜드 필터 + 가격 오름차순
-- 예상: type=ALL, Extra=Using filesort
-- =====================================================================
EXPLAIN
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.price ASC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.price ASC
LIMIT 20 OFFSET 0;


-- =====================================================================
-- ③ likes_desc : 브랜드 필터 + 좋아요순 (JOIN + GROUP BY + COUNT 정렬)
-- 예상: Extra=Using temporary; Using filesort  ← 인덱스로 못 잡는 핵심 병목
-- =====================================================================
EXPLAIN
SELECT p.* FROM products p
LEFT JOIN likes l ON l.product_id = p.id
WHERE p.deleted_at IS NULL AND p.brand_id = 7
GROUP BY p.id
ORDER BY COUNT(l.id) DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT p.* FROM products p
LEFT JOIN likes l ON l.product_id = p.id
WHERE p.deleted_at IS NULL AND p.brand_id = 7
GROUP BY p.id
ORDER BY COUNT(l.id) DESC
LIMIT 20 OFFSET 0;


-- =====================================================================
-- ④ (nice-to-have) deep pagination : OFFSET 누적 비용 확인
-- 예상: 앞 10,000 행을 만들어 버리는 비용이 actual time 에 그대로 드러남
-- =====================================================================
EXPLAIN ANALYZE
SELECT p.* FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 7
ORDER BY p.price ASC
LIMIT 20 OFFSET 10000;
