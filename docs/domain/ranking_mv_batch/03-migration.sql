-- =====================================================
-- 주간/월간 랭킹 배치 & MV — 운영 DB 마이그레이션 (non-local 전용)
--
-- 배경: modules/jpa/src/main/resources/jpa.yml 의 non-local 프로필은 ddl-auto: none 이라
-- 아래 DDL은 Hibernate가 자동 생성하지 않는다. dev/qa/prd 반영 전 이 스크립트를 그대로,
-- 또는 각 환경의 배포 파이프라인이 요구하는 형식으로 변환해 실행해야 한다.
--
-- 관련 문서: 01-design.md (Q&A #14 product_metrics 리네임, #16/#17 신규 테이블 PK,
--            #19 created_at/updated_at 추가, #23/#24 이번 세션 보완)
-- 참고: 원본 product_metrics DDL 출처는 docs/domain/outbox-kafka/01-design.md §4
--
-- 실행 전 체크리스트:
--   1. 반드시 스테이징/QA에서 먼저 검증한다.
--   2. product_metrics → product_metric_summary 구간은 서비스 트래픽이 적은 시간대에 실행한다
--      (RENAME TABLE 은 MySQL 8에서 메타데이터 잠금만 필요해 매우 빠르지만, 컬럼 변경(MODIFY)은
--      테이블 크기에 따라 락/리빌드 비용이 발생할 수 있다 — 대용량이면 pt-online-schema-change 등
--      온라인 DDL 도구 사용을 검토한다).
--   3. 이 파일은 최초 1회 반영을 전제로 작성되었다. 두 번째 실행 대비 IF NOT EXISTS 등은
--      의도적으로 넣지 않았다 — 실행 여부를 스크립트가 아니라 운영자가 추적해야 한다.
-- =====================================================

-- -----------------------------------------------------
-- 1. product_metrics → product_metric_summary 리네임 + 컬럼 정비 (Q&A #14, #19)
-- -----------------------------------------------------
RENAME TABLE product_metrics TO product_metric_summary;

ALTER TABLE product_metric_summary
    CHANGE COLUMN ref_product_id product_id VARCHAR(60) NOT NULL COMMENT 'products PK 참조';

-- created_at이 원래 없었으므로, 기존 행은 updated_at 값으로 채워 넣은 뒤 NOT NULL로 확정한다.
ALTER TABLE product_metric_summary
    ADD COLUMN created_at DATETIME(6) NULL AFTER purchase_count;

UPDATE product_metric_summary
SET created_at = updated_at
WHERE created_at IS NULL;

ALTER TABLE product_metric_summary
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

-- -----------------------------------------------------
-- 2. product_metric_daily 신규 생성 (Q&A #16)
-- -----------------------------------------------------
CREATE TABLE product_metric_daily
(
    metric_date        DATE     NOT NULL COMMENT '일자',
    product_id         VARCHAR(60) NOT NULL COMMENT 'FK 아님, 상품 참조용 문자열 ID',
    view_count         BIGINT   NOT NULL DEFAULT 0 COMMENT '조회수 (그날)',
    like_delta_count   BIGINT   NOT NULL DEFAULT 0 COMMENT '좋아요 증감 합 (+1/-1 누적, 음수 가능)',
    purchase_quantity  BIGINT   NOT NULL DEFAULT 0 COMMENT '구매 수량 (그날)',
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (metric_date, product_id)
) ENGINE = InnoDB;

-- -----------------------------------------------------
-- 3. mv_product_rank_weekly / mv_product_rank_monthly 신규 생성 (Q&A #17)
-- -----------------------------------------------------
CREATE TABLE mv_product_rank_weekly
(
    as_of_date             DATE        NOT NULL COMMENT '배치 실행 기준일 (스냅샷)',
    product_id             VARCHAR(60) NOT NULL COMMENT '상품 참조용 문자열 ID',
    score                  DOUBLE      NOT NULL DEFAULT 0 COMMENT '최근 7일(어제 기준) 가중합 score',
    view_sum               BIGINT      NOT NULL DEFAULT 0 COMMENT '최근 7일 조회수 합',
    like_delta_sum         BIGINT      NOT NULL DEFAULT 0 COMMENT '최근 7일 좋아요 증감 합',
    purchase_quantity_sum  BIGINT      NOT NULL DEFAULT 0 COMMENT '최근 7일 구매 수량 합',
    created_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (as_of_date, product_id),
    INDEX idx_mv_weekly_asofdate_score (as_of_date, score DESC)
) ENGINE = InnoDB;

CREATE TABLE mv_product_rank_monthly
(
    as_of_date             DATE        NOT NULL COMMENT '배치 실행 기준일 (스냅샷)',
    product_id             VARCHAR(60) NOT NULL COMMENT '상품 참조용 문자열 ID',
    score                  DOUBLE      NOT NULL DEFAULT 0 COMMENT '최근 30일(어제 기준) 가중합 score',
    view_sum               BIGINT      NOT NULL DEFAULT 0 COMMENT '최근 30일 조회수 합',
    like_delta_sum         BIGINT      NOT NULL DEFAULT 0 COMMENT '최근 30일 좋아요 증감 합',
    purchase_quantity_sum  BIGINT      NOT NULL DEFAULT 0 COMMENT '최근 30일 구매 수량 합',
    created_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (as_of_date, product_id),
    INDEX idx_mv_monthly_asofdate_score (as_of_date, score DESC)
) ENGINE = InnoDB;
