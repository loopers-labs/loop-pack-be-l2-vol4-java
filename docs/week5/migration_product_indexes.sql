-- =============================================================================
-- 운영(prd, ddl-auto: none) DDL — 상품 목록 조회 최적화 인덱스 (week5)
-- =============================================================================
-- local/test 는 resources/import.sql 로 자동 생성되지만, 스키마를 Hibernate 가 관리하지 않는
-- 환경(prd)에서는 이 스크립트를 수동/배포 파이프라인으로 적용한다.
--
-- 정렬 컬럼(likes_count, id)에 방향(DESC)을 지정한 내림차순 인덱스다. ORDER BY likes_count DESC,
-- id DESC 를 forward index scan 으로 충족 → 풀스캔·filesort 제거. (근거: docs/week5/05-index-optimization.md)
--
-- 대용량 테이블이면 온라인 DDL 영향(INPLACE/락)을 고려해 트래픽 한산 시간대에 적용할 것.
-- =============================================================================

CREATE INDEX idx_brand_active_likes_desc ON product (brand_id, deleted_at, likes_count DESC, id DESC);
CREATE INDEX idx_active_likes_desc       ON product (deleted_at, likes_count DESC, id DESC);
