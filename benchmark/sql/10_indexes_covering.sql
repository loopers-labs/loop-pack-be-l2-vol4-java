-- =============================================================================
-- 10. 커버링 인덱스 생성 (비커버링 DESC 복합 ↔ 커버링 DESC 복합 비교용)
-- =============================================================================
-- 07 까지의 결론: DESC 복합 인덱스로 정렬(filesort)·풀스캔은 제거됐다. 하지만 쿼리가
-- SELECT * 이므로 인덱스로 정렬을 충족한 뒤 매 행마다 PK(클러스터) 테이블 룩업이 남는다.
--
-- 여기서는 "커버링 인덱스" 효과를 본다. 즉 SELECT 가 읽는 컬럼을 인덱스 키에 모두 포함시켜
-- 테이블 룩업 없이 인덱스만으로 응답("Using index")하게 만든다.
--   - 목록 화면이 실제로 쓰는 컬럼만 노출한다고 가정: id, brand_id, name, price, likes_count
--     (description varchar(2000), image_url varchar(500) 은 목록에서 제외 → 커버링 대상에서 뺀다)
--   - 정렬 컬럼(likes_count, id)은 DESC, 커버 컬럼(price, name)은 끝에 덧붙인다.
--
-- 비교를 위해 같은 테이블에 "비커버링"과 "커버링" 인덱스를 함께 두고, 11 에서 FORCE INDEX 로
-- 각각 강제해 측정한다. (둘 다 있으면 옵티마이저는 보통 커버링을 택한다.)
-- =============================================================================

USE loopers_bench;

-- 이전 단계(04/06/08)에서 남았을 수 있는 보조 인덱스 정리 (없으면 무시)
DROP INDEX idx_brand_id           ON product;
DROP INDEX idx_likes_count        ON product;
DROP INDEX idx_deleted_at         ON product;

-- 비커버링 DESC 복합 (07 과 동일 — baseline 재현)
CREATE INDEX idx_brand_active_likes_desc ON product (brand_id, deleted_at, likes_count DESC, id DESC);
CREATE INDEX idx_active_likes_desc       ON product (deleted_at, likes_count DESC, id DESC);

-- 커버링 DESC 복합 (정렬 컬럼 + 목록 노출 컬럼 포함)
-- 주의: 커버링하려면 SELECT/WHERE/ORDER BY 가 참조하는 모든 컬럼이 인덱스에 있어야 한다.
--   - A(브랜드 필터): brand_id 가 인덱스 선두라 자동 포함 → price, name 만 덧붙이면 커버.
--   - B(전체)       : 목록 SELECT 에 brand_id 가 있는데 정렬 인덱스엔 없다 → brand_id 도 포함해야
--                     커버된다. 하나라도 빠지면 PK 룩업으로 되돌아가 커버링 효과가 사라진다.
CREATE INDEX idx_brand_cover ON product (brand_id, deleted_at, likes_count DESC, id DESC, price, name);
CREATE INDEX idx_active_cover ON product (deleted_at, likes_count DESC, id DESC, brand_id, price, name);

ANALYZE TABLE product;

SELECT '=== 인덱스별 크기 (테이블 vs 인덱스 트레이드오프) ===' AS '';
SELECT
    index_name,
    ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 1) AS size_mb,
    stat_value AS leaf_pages
FROM mysql.innodb_index_stats
WHERE database_name = 'loopers_bench'
  AND table_name = 'product'
  AND stat_name = 'size'
ORDER BY stat_value DESC;

SHOW INDEX FROM product;
