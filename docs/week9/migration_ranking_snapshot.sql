-- =============================================================================
-- week9 — ranking_daily_snapshot (일간 랭킹 영속 스냅샷)
-- =============================================================================
-- 랭킹 ZSET(ranking:all:{yyyyMMdd})은 TTL 2일이라 그 뒤엔 사라진다. 확정된 하루치 상위 N 을
-- commerce-batch(rankingSnapshotJob)가 이 테이블로 내려 과거 조회를 가능하게 한다.
--
--   쓰기: commerce-batch (JdbcTemplate, delete-then-insert 로 재실행 안전)
--   읽기: commerce-api  (RankingSnapshotEntity / RankingCompositeRepository)
--
-- local/test 는 ddl-auto:create + import.sql 이 만든다. 이 파일은 운영(prd) 적용용.
-- 설계 배경: docs/week9/04-redis-model.md §5, docs/week9/05-implementation-notes.md §2.6

CREATE TABLE IF NOT EXISTS ranking_daily_snapshot (
    ranking_date DATE        NOT NULL,
    product_id   BIGINT      NOT NULL,
    rank_no      INT         NOT NULL,           -- `rank` 는 MySQL 8 예약어(RANK() 윈도우 함수)
    score        DOUBLE      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (ranking_date, product_id)       -- 같은 날짜 중복 적재 방지(멱등)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 일자 + 순위순 페이지 조회용. PK 가 (ranking_date, product_id) 라 순위 정렬을 못 타므로 별도로 필요하다.
-- 커버링은 노리지 않는다 — 페이지당 최대 size(기본 20)행이라 lookup 비용이 무의미하다.
CREATE INDEX idx_rds_date_rank ON ranking_daily_snapshot (ranking_date, rank_no);

-- =============================================================================
-- 보관 정책 (운영 판단 필요)
-- =============================================================================
-- 이 테이블은 자동으로 줄지 않는다. ZSET 의 TTL 이 하던 회수 역할을 대신할 주체가 없다.
-- 상위 100 × 365일 ≈ 36,500행/년 이라 방치해도 당장은 문제없지만, 무한 증가는 정책이 아니다.
-- 보관 기간을 정하면 아래를 주기 배치/이벤트 스케줄러로 돌린다.
--
--   DELETE FROM ranking_daily_snapshot WHERE ranking_date < CURRENT_DATE - INTERVAL 1 YEAR;
