-- =============================================================================
-- Hibernate import.sql — 스키마 생성 직후(ddl-auto: create, local/test) 자동 실행된다.
-- =============================================================================
-- ※ Hibernate 의 기본 import.sql 파서는 "한 줄 = 한 문장" 규칙을 따른다(문장을 여러 줄로 쪼개지 말 것).
-- ※ ddl-auto: create 는 부팅마다 테이블을 새로 만들므로 인덱스도 매번 새로 생성된다(충돌 없음).
--
-- ※ week5 의 product 좋아요순 복합 인덱스(idx_brand_active_likes_desc / idx_active_likes_desc)는
--    week7 CQRS 전환으로 좋아요순 리스팅이 product_metrics 로 이전되며 제거됐다(아래 product_metrics 인덱스로 대체).

-- ShedLock 분산 락 테이블(reconcile 스케줄러용). JPA 엔티티가 아니라 ddl-auto:create 가 만들지 않으므로 직접 생성.
-- Hibernate 가 관리하지 않아 부팅 간 drop 되지 않을 수 있어 'if not exists' 로 멱등하게 만든다. (운영: migration_shedlock.sql)
create table if not exists shedlock (name varchar(64) not null, lock_until timestamp(3) not null, locked_at timestamp(3) not null, locked_by varchar(255) not null, primary key (name));

-- =============================================================================
-- week7 이벤트 기반 아키텍처
-- =============================================================================
-- product_metrics 상품 리스팅 read model(CQRS)의 좋아요순 복합 인덱스. product 와 동일한 이유로 방향(DESC)을
-- DDL 로 직접 지정한다(Hibernate @Index 는 방향 표현 불가). product_metrics 테이블 자체는 ProductMetricsEntity 로
-- ddl-auto:create 가 만들고, 여기서 인덱스만 얹는다. (운영: docs/week7/migration_product_metrics.sql)
create index idx_pm_active_likes_desc on product_metrics (deleted_at, like_count desc, product_id desc);
create index idx_pm_brand_active_likes_desc on product_metrics (brand_id, deleted_at, like_count desc, product_id desc);

-- event_handled — 컨슈머 멱등 저장소. (consumer_group, event_id) 복합 PK 로 group 별 1회 처리 보장.
-- JPA 엔티티가 아니라(commerce-streamer 가 JdbcTemplate 로 write) 직접 생성한다. (운영: docs/week7/migration_event_handled.sql)
create table if not exists event_handled (consumer_group varchar(100) not null, event_id bigint not null, handled_at timestamp(3) not null, primary key (consumer_group, event_id));
