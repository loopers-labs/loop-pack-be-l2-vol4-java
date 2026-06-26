-- =============================================================================
-- Hibernate import.sql — 스키마 생성 직후(ddl-auto: create, local/test) 자동 실행된다.
-- =============================================================================
-- week5 상품 목록 조회 최적화 인덱스. JPA @Index 는 컬럼 방향(DESC)을 표현하지 못하므로
-- 내림차순(DESC) 인덱스를 DDL 로 직접 만든다. (forward index scan → 딥 페이지에서 backward 보다 유리,
-- mixed-direction 정렬도 filesort 없이 처리 가능)
-- 설계·측정 근거: docs/week5/05-index-optimization.md
--
-- ※ Hibernate 의 기본 import.sql 파서는 "한 줄 = 한 문장" 규칙을 따른다(문장을 여러 줄로 쪼개지 말 것).
-- ※ ddl-auto: create 는 부팅마다 테이블을 새로 만들므로 인덱스도 매번 새로 생성된다(충돌 없음).
-- ※ 운영(prd, ddl-auto: none)에는 docs/week5/migration_product_indexes.sql 를 별도로 적용한다.
create index idx_brand_active_likes_desc on product (brand_id, deleted_at, likes_count desc, id desc);
create index idx_active_likes_desc on product (deleted_at, likes_count desc, id desc);

-- ShedLock 분산 락 테이블(reconcile 스케줄러용). JPA 엔티티가 아니라 ddl-auto:create 가 만들지 않으므로 직접 생성.
-- Hibernate 가 관리하지 않아 부팅 간 drop 되지 않을 수 있어 'if not exists' 로 멱등하게 만든다. (운영: migration_shedlock.sql)
create table if not exists shedlock (name varchar(64) not null, lock_until timestamp(3) not null, locked_at timestamp(3) not null, locked_by varchar(255) not null, primary key (name));
