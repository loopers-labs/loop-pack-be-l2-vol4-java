-- =============================================================================
-- ShedLock 분산 락 테이블 — reconcile 자동 스케줄러(PaymentReconcileScheduler)용.
-- 운영(prd, ddl-auto: none)에 한 번 적용한다. local/test 는 import.sql 이 부팅 시 생성한다.
--
-- 멀티 인스턴스에서 @Scheduled reconcile 회차를 한 인스턴스만 실행하도록 ShedLock 이 이 테이블로
-- 락 상태를 공유한다(JdbcTemplateLockProvider, usingDbTime). 컬럼 스키마는 ShedLock JDBC 기본 규격.
-- =============================================================================
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
