-- =============================================================================
-- 04. 인덱스 추가 (개선)
-- =============================================================================
-- 대상 쿼리:
--   A) WHERE brand_id = ? AND deleted_at IS NULL ORDER BY likes_count DESC, id DESC
--   B) WHERE deleted_at IS NULL                  ORDER BY likes_count DESC, id DESC
--
-- 설계 원칙: "동등 조건 컬럼 → 정렬 컬럼 → tie-break 컬럼" 순서로 복합 인덱스를 구성하면
--           WHERE 의 등치 조건으로 범위를 좁힌 뒤 ORDER BY 를 인덱스 순서로 그대로 충족 →
--           풀스캔과 filesort 가 동시에 사라진다.
--
--   idx_brand_active_likes (brand_id, deleted_at, likes_count, id)
--     · brand_id    = ?        (등치)
--     · deleted_at IS NULL     (등치성 — NULL ref)
--     · likes_count, id        (ORDER BY 충족; DESC 정렬은 backward index scan 으로 처리)
--
--   idx_active_likes (deleted_at, likes_count, id)
--     · brand_id 가 없는 전체 목록(B)용. idx_brand_active_likes 는 선행 컬럼이 brand_id 라
--       B 쿼리에 쓸 수 없으므로 별도 인덱스가 필요하다.
--
-- ※ 운영 엔티티(ProductEntity.@Table(indexes=...))에도 동일 컬럼 구성으로 반영했다.
--   Hibernate @Index 는 ASC 컬럼만 생성하지만, MySQL 은 ORDER BY ... DESC 를
--   backward index scan 으로 처리하므로 filesort 는 동일하게 제거된다.
-- =============================================================================

USE loopers_bench;

CREATE INDEX idx_brand_active_likes ON product (brand_id, deleted_at, likes_count, id);
CREATE INDEX idx_active_likes       ON product (deleted_at, likes_count, id);

ANALYZE TABLE product;

SHOW INDEX FROM product;
