-- ============================================================
-- Loopers 시딩 스크립트 v3
-- 브랜드 20개 / 상품 100만개 / 재고 100만개
--
-- 변경: Cross JOIN 6단계 (10^6 = 1,000,000)
--       STEP 0에 TRUNCATE 추가 (재실행 안전)
--
-- 실행: 전체 선택(Ctrl+A) -> Alt+X (Execute SQL Script)
-- ============================================================

-- ----------------------------------------------------------
-- STEP 0: 기존 데이터 초기화 (FK 순서: stocks → products)
-- ----------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE stocks;
TRUNCATE TABLE products;
SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------
-- STEP 1: 브랜드 20개 삽입
--   INSERT IGNORE -> name unique 충돌 시 스킵 (재실행 안전)
-- ----------------------------------------------------------
INSERT IGNORE INTO brands (id, name, description, created_at, updated_at)
VALUES
  (UUID_TO_BIN(UUID()), 'Nike',          '글로벌 스포츠 브랜드',        NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Adidas',        '독일 스포츠 브랜드',          NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Puma',          '독일 스포츠웨어 브랜드',      NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'New Balance',   '미국 러닝화 브랜드',          NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Reebok',        '영국 스포츠 브랜드',          NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Under Armour',  '미국 퍼포먼스웨어 브랜드',   NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Fila',          '이탈리아 스포츠 브랜드',      NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Asics',         '일본 러닝화 브랜드',          NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Skechers',      '미국 캐주얼 슈즈 브랜드',    NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Vans',          '미국 스케이트 브랜드',        NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Converse',      '미국 클래식 슈즈 브랜드',    NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Timberland',    '미국 아웃도어 브랜드',        NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'The North Face','미국 아웃도어 브랜드',        NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Patagonia',     '미국 친환경 아웃도어 브랜드', NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Columbia',      '미국 아웃도어 브랜드',        NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Levis',         '미국 데님 브랜드',            NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Zara',          '스페인 패스트패션 브랜드',    NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'H&M',           '스웨덴 패스트패션 브랜드',    NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Uniqlo',        '일본 캐주얼 브랜드',          NOW(), NOW()),
  (UUID_TO_BIN(UUID()), 'Gap',           '미국 캐주얼 브랜드',          NOW(), NOW());

-- ----------------------------------------------------------
-- STEP 2: 상품 100만개 삽입
--
-- 숫자 생성: Cross JOIN 6단계 (10^6 = 1,000,000)
-- 브랜드 매핑: n % 20 -> 인라인 서브쿼리
--
-- 분포:
--   brand_id  : 20개 브랜드 완전 균등 (n % 20)
--   price     : 10,000 ~ 990,000 (만원 단위)
--   like_count: 0 ~ 10,000 균등
--   created_at: 최근 1년 내 랜덤
--   deleted_at: 10% 소프트딜리트
-- ----------------------------------------------------------
INSERT INTO products (id, brand_id, name, description, price, like_count, likes_purged, created_at, updated_at, deleted_at)
SELECT
    UUID_TO_BIN(UUID()),
    b.id,
    CONCAT('상품_', LPAD(nums.n + 1, 7, '0')),
    CONCAT('상품 ', nums.n + 1, '번 상세 설명입니다.'),
    (FLOOR(RAND() * 99) + 1) * 10000,
    FLOOR(RAND() * 10001),
    0,
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY,
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY,
    IF(RAND() < 0.1, NOW() - INTERVAL FLOOR(RAND() * 30) DAY, NULL)
FROM (
    SELECT a.n + b.n*10 + c.n*100 + d.n*1000 + e.n*10000 + f.n*100000 AS n
    FROM
        (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
        CROSS JOIN
        (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
        CROSS JOIN
        (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
        CROSS JOIN
        (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
        CROSS JOIN
        (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e
        CROSS JOIN
        (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) f
) AS nums
JOIN (
    SELECT (ROW_NUMBER() OVER (ORDER BY created_at) - 1) AS idx, id
    FROM brands
    WHERE deleted_at IS NULL
    ORDER BY created_at
    LIMIT 20
) AS b ON b.idx = nums.n % 20;

-- ----------------------------------------------------------
-- STEP 3: 재고 100만개 삽입 (상품 1:1)
--   total_quantity: 1 ~ 1,000 균등
--   이미 재고 있는 상품은 스킵
-- ----------------------------------------------------------
INSERT INTO stocks (id, product_id, total_quantity, reserved_quantity, created_at, updated_at)
SELECT
    UUID_TO_BIN(UUID()),
    p.id,
    FLOOR(RAND() * 1000) + 1,
    0,
    NOW(),
    NOW()
FROM products p
WHERE NOT EXISTS (SELECT 1 FROM stocks s WHERE s.product_id = p.id);

-- ----------------------------------------------------------
-- STEP 4: 결과 확인
-- ----------------------------------------------------------
SELECT '브랜드'   AS 테이블, COUNT(*) AS 건수 FROM brands  WHERE deleted_at IS NULL
UNION ALL
SELECT '전체 상품', COUNT(*) FROM products
UNION ALL
SELECT '활성 상품', COUNT(*) FROM products WHERE deleted_at IS NULL
UNION ALL
SELECT '재고',     COUNT(*) FROM stocks;
