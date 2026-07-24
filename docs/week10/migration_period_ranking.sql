-- =============================================================================
-- week10 — 기간 랭킹(주간/월간) 스키마
-- =============================================================================
-- local/test 는 ddl-auto:create + import.sql 이 만든다. 이 파일은 운영(prd) 적용용.
-- 설계 배경: docs/week10/01-requirements.md, docs/week10/03-batch-design.md

-- -----------------------------------------------------------------------------
-- 1) product_metrics_daily — 일자별 상품 지표(주간/월간 집계의 원천)
-- -----------------------------------------------------------------------------
-- product_metrics(상품당 1행, 누적)와 달리 "그날 발생한 양"만 담는다. 그래야 임의 구간을 SUM 으로 합산할 수 있다.
--   쓰기: commerce-streamer (MetricsAggregator, INSERT ... ON DUPLICATE KEY UPDATE)
--   읽기: commerce-batch    (기간 랭킹 Job 의 Reader)
--
-- like_delta 는 좋아요 취소가 있어 음수가 될 수 있다(signed).
-- metric_date 는 이벤트 발생시각(KST) 기준 — 소비 시각으로 잡으면 컨슈머 지연이 자정을 넘길 때 버킷이 밀린다.
--
-- order_score 를 sales_amount 와 별도로 두는 이유: log 는 합과 교환되지 않는다(Σ log(xᵢ) ≠ log(Σ xᵢ)).
-- 배치가 SUM(sales_amount) 에 log10 을 적용하면 실시간 일간 랭킹(주문 건별 log)과 산식이 달라진다.
-- 이벤트를 보는 시점에 건별 스코어를 더해 두면 기간 집계는 단순 SUM 으로 끝나고 스코어 정의가 일관된다.
-- sales_amount 는 원자료로 남겨 정책 변경 시 재계산할 수 있게 한다.
CREATE TABLE IF NOT EXISTS product_metrics_daily (
    metric_date  DATE        NOT NULL,
    product_id   BIGINT      NOT NULL,
    view_count   BIGINT      NOT NULL DEFAULT 0,
    like_delta   BIGINT      NOT NULL DEFAULT 0,   -- 취소가 많으면 음수
    sales_count  BIGINT      NOT NULL DEFAULT 0,
    sales_amount BIGINT      NOT NULL DEFAULT 0,   -- 단가×수량 합 (원자료)
    order_score  DOUBLE      NOT NULL DEFAULT 0,   -- Σ 0.6 × log10(1+건별매출)
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (metric_date, product_id)          -- 선두가 날짜 → 구간 스캔이 PK 로 커버된다(별도 인덱스 불필요)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- 2) mv_product_rank_weekly / mv_product_rank_monthly — 기간 랭킹 MV(TOP 100)
-- -----------------------------------------------------------------------------
-- 조회 시점 계산을 피하기 위한 조회 전용 테이블. 배치가 미리 순위를 확정해 두면
-- 조회는 (period_start, rank_no) 인덱스 레인지 스캔 한 번으로 끝난다.
--
-- period_start = 기간 식별자. 주간은 그 주 월요일(ISO-8601), 월간은 그 달 1일.
-- rank 는 MySQL 8 예약어(RANK() 윈도우 함수)라 컬럼명은 rank_no 를 쓴다.
CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
    period_start DATE        NOT NULL,
    product_id   BIGINT      NOT NULL,
    period_end   DATE        NOT NULL,
    rank_no      INT         NOT NULL,
    score        DOUBLE      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (period_start, product_id)         -- 같은 기간 중복 적재 방지(멱등)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_mvw_period_rank ON mv_product_rank_weekly (period_start, rank_no);

CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
    period_start DATE        NOT NULL,
    product_id   BIGINT      NOT NULL,
    period_end   DATE        NOT NULL,
    rank_no      INT         NOT NULL,
    score        DOUBLE      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (period_start, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_mvm_period_rank ON mv_product_rank_monthly (period_start, rank_no);

-- -----------------------------------------------------------------------------
-- 3) product_rank_staging — 배치 Step1 → Step2 중간 적재
-- -----------------------------------------------------------------------------
-- 랭킹은 전역 정렬이 필요한데 청크 처리는 스트리밍이라 한 Step 으로 순위를 매길 수 없다.
-- Step1(청크)이 전 상품 스코어를 여기 쌓고, Step2 가 ROW_NUMBER() 로 상위 N 순위를 확정해 MV 로 옮긴다.
-- period_type 으로 주간/월간 잡이 같은 테이블을 공유한다(동시 실행 시에도 서로 침범하지 않음).
CREATE TABLE IF NOT EXISTS product_rank_staging (
    period_type  VARCHAR(10) NOT NULL,             -- WEEKLY | MONTHLY
    period_start DATE        NOT NULL,
    product_id   BIGINT      NOT NULL,
    score        DOUBLE      NOT NULL,
    PRIMARY KEY (period_type, period_start, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- =============================================================================
-- 보관 정책 (운영 판단 필요)
-- =============================================================================
-- product_metrics_daily 는 상품수 × 일수로 계속 증가한다. MV(주 100행 + 월 100행)와 달리 무시할 수 없다.
-- 상품 10만 개가 매일 활동하면 연 3,650만 행이다. 보관 기간을 정하면 아래를 주기 배치로 돌린다.
--
--   DELETE FROM product_metrics_daily WHERE metric_date < CURRENT_DATE - INTERVAL 1 YEAR;
--
-- 월간 랭킹이 최대 31일치만 보므로 실제 필요한 보관 기간은 그보다 훨씬 짧다. 다만 과거 기간 랭킹을
-- 재계산(백필)할 여지를 남기려면 여유를 두는 편이 낫다.
--
-- staging 은 배치가 매 실행 시 해당 (period_type, period_start) 를 지우고 쓰므로 누적되지 않는다.
