-- =============================================================================
-- 02. 데이터 시딩 — 브랜드 50개 + 상품 120,000건 (각 컬럼 다양하게 분포)
-- =============================================================================
-- 요구사항: 상품 10만건 이상, 컬럼 값이 다양하게 분포.
--
-- 분포 설계 (실측 쿼리의 인덱스 효과가 의미 있게 드러나도록 의도적으로 편중시킴):
--   - brand_id    : 1~50. POW(RAND(),2) 로 저번호 브랜드에 더 몰리게 함
--                   → "인기 브랜드는 상품이 많고, 비인기 브랜드는 적다"는 현실 반영.
--   - likes_count : 0 ~ 50,000. POW(RAND(),3) 으로 멱급수 편중
--                   → 대부분 0 근처, 극소수만 폭발적(바이럴). 좋아요순 정렬 의미 있음 + 동점 다수.
--   - price       : 1,000 ~ 100,900 (100원 단위) 다양.
--   - deleted_at  : 약 8% 만 soft-delete (NULL 아님) → `deleted_at IS NULL` 필터가 실제로 행을 거름.
--   - created_at  : 최근 730일에 걸쳐 분산.
--
-- 생성 방식: 0~9 숫자 테이블 6개를 cross join 해 시퀀스를 만들고 INSERT ... SELECT 로
--            집합 기반 일괄 삽입 (행 단위 루프보다 수십 배 빠름).
-- =============================================================================

USE loopers_bench;

-- 0~9 헬퍼 (시퀀스 생성용)
-- ※ TEMPORARY 테이블은 한 쿼리에서 두 번 이상 참조 불가(Can't reopen table)라 self-join 이
--   필요한 cross join 에 쓸 수 없다. 일반 테이블로 만들고 시딩 끝에 DROP 한다.
DROP TABLE IF EXISTS digits;
CREATE TABLE digits (n INT);
INSERT INTO digits (n) VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

-- ---------------------------------------------------------------------------
-- 브랜드 50개
-- ---------------------------------------------------------------------------
INSERT INTO brand (name, description, created_at, updated_at, deleted_at)
SELECT
    CONCAT('브랜드-', seq + 1),
    CONCAT('벤치마크용 브랜드 ', seq + 1, ' 설명'),
    NOW(6) - INTERVAL FLOOR(RAND() * 730) DAY,
    NOW(6),
    NULL
FROM (
    SELECT a.n + b.n * 10 AS seq
    FROM digits a CROSS JOIN digits b
) s
WHERE seq < 50;

-- ---------------------------------------------------------------------------
-- 상품 120,000건
-- ---------------------------------------------------------------------------
INSERT INTO product (brand_id, name, description, image_url, price, likes_count, created_at, updated_at, deleted_at)
SELECT
    1 + FLOOR(POW(RAND(), 2) * 50)                              AS brand_id,
    CONCAT('상품-', seq)                                        AS name,
    CONCAT('벤치마크용 상품 ', seq, ' 상세 설명 텍스트')        AS description,
    CONCAT('https://img.loopers.test/p/', seq, '.jpg')          AS image_url,
    1000 + FLOOR(RAND() * 1000) * 100                           AS price,
    FLOOR(POW(RAND(), 3) * 50000)                               AS likes_count,
    NOW(6) - INTERVAL FLOOR(RAND() * 730) DAY                   AS created_at,
    NOW(6)                                                      AS updated_at,
    IF(RAND() < 0.08, NOW(6) - INTERVAL FLOOR(RAND() * 365) DAY, NULL) AS deleted_at
FROM (
    SELECT a.n + b.n*10 + c.n*100 + d.n*1000 + e.n*10000 + f.n*100000 AS seq
    FROM digits a
    CROSS JOIN digits b
    CROSS JOIN digits c
    CROSS JOIN digits d
    CROSS JOIN digits e
    CROSS JOIN digits f
) s
WHERE seq < 120000;

DROP TABLE IF EXISTS digits;

-- 분포 확인용 요약
SELECT '총 상품 수' AS metric, COUNT(*) AS value FROM product
UNION ALL SELECT '활성 상품 수(deleted_at IS NULL)', COUNT(*) FROM product WHERE deleted_at IS NULL
UNION ALL SELECT '브랜드 수', COUNT(*) FROM brand
UNION ALL SELECT '최대 likes_count', MAX(likes_count) FROM product
UNION ALL SELECT 'likes_count=0 비율(%)', ROUND(100 * SUM(likes_count = 0) / COUNT(*)) FROM product;

-- 가장 상품이 많은 브랜드(개선 전후 측정에 쓸 brand_id 후보)
SELECT brand_id, COUNT(*) AS cnt
FROM product
WHERE deleted_at IS NULL
GROUP BY brand_id
ORDER BY cnt DESC
LIMIT 5;
