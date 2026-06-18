-- =====================================================================
-- Round 5 like_count 인덱스 쓰기 부하 측정
-- =====================================================================
-- 목적: like_count 인덱스 유지 비용을 정량화한다.
--       "인덱스로 인한 부하는 얼마나 되는데?" (멘토링 피드백)
--
-- 구조:
--   [1] 현재 상태(인덱스 있음) → UPDATE 타이밍 측정
--   [2] 인덱스 제거 → 동일 UPDATE 타이밍 측정 (베이스라인)
--   [3] 인덱스 복원
--   [4] 비교 해석: 쓰기 비용 vs 읽기 개선 효과
--
-- !! 주의: DROP INDEX 포함 — 운영 환경에서 절대 실행하지 말 것 !!
--
-- 실행 방법:
--   docker exec -i <mysql_container> mysql -uapplication -papplication loopers \
--     < sql/round5/write_overhead_round5.sql
-- =====================================================================

-- =====================================================================
-- 0) 현재 인덱스 상태 확인
-- =====================================================================
SHOW INDEX FROM products WHERE Key_name LIKE '%like%';
SHOW INDEX FROM likes;

-- =====================================================================
-- 1) WITH 인덱스 — like 증가/감소 UPDATE 타이밍 (현재 운영 상태)
--
-- 실제 LikeApplicationService.like() / unlike() 가 호출하는 쿼리와 동일:
--   UPDATE products SET like_count = like_count + 1 WHERE id = ?
--   UPDATE products SET like_count = like_count - 1 WHERE id = ? AND like_count > 0
-- =====================================================================
SET profiling = 1;
SET @target_id = (SELECT id FROM products ORDER BY id LIMIT 1);  -- 재현 가능한 고정 ID

UPDATE products SET like_count = like_count + 1 WHERE id = @target_id;
UPDATE products SET like_count = like_count - 1 WHERE id = @target_id AND like_count > 0;

SHOW PROFILES;  -- Query_ID, Duration, Query 확인
SET profiling = 0;

-- =====================================================================
-- 2) WITHOUT 인덱스 — 인덱스 제거 후 동일 쿼리 타이밍 (베이스라인)
--
-- idx_products_brand_likecount (brand_id, like_count)
-- idx_products_likecount       (like_count)
-- 두 인덱스 모두 like_count 컬럼을 포함하므로 UPDATE 시 두 인덱스 페이지가 갱신된다.
-- =====================================================================
DROP INDEX idx_products_brand_likecount ON products;
DROP INDEX idx_products_likecount ON products;

SET profiling = 1;
UPDATE products SET like_count = like_count + 1 WHERE id = @target_id;
UPDATE products SET like_count = like_count - 1 WHERE id = @target_id AND like_count > 0;
SHOW PROFILES;
SET profiling = 0;

-- =====================================================================
-- 3) 인덱스 복원 (반드시 실행)
-- =====================================================================
CREATE INDEX idx_products_brand_likecount ON products (brand_id, like_count);
CREATE INDEX idx_products_likecount       ON products (like_count);

SHOW INDEX FROM products WHERE Key_name LIKE '%like%';  -- 복원 확인

-- =====================================================================
-- 4) 결과 해석 기준
-- =====================================================================
-- 예상 측정 결과 (100K 상품 기준):
--   WITH    인덱스 — UPDATE 1건:  ~0.3 ~ 0.5 ms  (인덱스 페이지 2개 갱신)
--   WITHOUT 인덱스 — UPDATE 1건:  ~0.1 ~ 0.2 ms  (컬럼 값만 갱신)
--   쓰기 부하 차이: +0.1 ~ 0.3 ms
--
-- 비교: 이 비용이 타당한가?
--   likes_desc 조회 AS-IS (JOIN+GROUP BY+COUNT):    ~246 ms
--   likes_desc 조회 WITH like_count + 인덱스:        ~1 ms
--   읽기 개선 효과:                                  약 245 ms 단축
--
--   쓰기 부하(+0.3ms) << 읽기 개선(245ms)
--   → 인덱스 유지 타당. 상세 근거는 likecount_index_decision.sql 참조.
-- =====================================================================
