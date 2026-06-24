-- =====================================================================
-- Round 5 — 좋아요 수 "표시용" 집계(countByProductIdIn) AS-IS 측정
-- =====================================================================
-- 목록 조회 시 상품 20개의 좋아요 수를 IN + GROUP BY 로 일괄 집계하는 현재 방식.
-- 비정규화(like_count 컬럼) 후엔 이 쿼리가 통째로 사라지므로, 그 before 비용을 기록한다.
-- IN 대상은 좋아요가 가장 많은 상위 20개 상품(id 1~20, worst case)으로 측정.
--
-- likes 의 인덱스는 (user_id, product_id) UK 뿐 → product_id 가 선두가 아니라
-- product_id 단독 조건은 인덱스를 효율적으로 못 탈 수 있음(이 점도 측정으로 확인).
-- =====================================================================

EXPLAIN
SELECT l.product_id, COUNT(*)
FROM likes l
WHERE l.product_id IN (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20)
GROUP BY l.product_id;

EXPLAIN ANALYZE
SELECT l.product_id, COUNT(*)
FROM likes l
WHERE l.product_id IN (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20)
GROUP BY l.product_id;
