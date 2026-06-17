-- =============================================================================
-- 01. 벤치마크 스키마 (베이스라인 — 보조 인덱스 없음)
-- =============================================================================
-- 앱의 `loopers` DB(ddl-auto: create 로 부팅마다 와이프됨)와 분리된 전용 벤치마크
-- 스키마를 만든다. product/brand 테이블은 운영 엔티티(ProductEntity/BrandEntity)와
-- 동일한 컬럼 구성을 미러링하되, 이 단계에서는 PK(id) 외 어떤 인덱스도 만들지 않는다.
-- → "개선 전" EXPLAIN 에서 풀스캔 + filesort 가 그대로 드러나도록 하기 위함.
--
-- 인덱스는 04_indexes.sql 에서 별도로 추가한다.
-- =============================================================================

DROP DATABASE IF EXISTS loopers_bench;
CREATE DATABASE loopers_bench CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE loopers_bench;

-- brand : 운영 BrandEntity 미러
CREATE TABLE brand (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- product : 운영 ProductEntity 미러 (재고는 별도 Stock Aggregate 이므로 제외)
CREATE TABLE product (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    brand_id    BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    image_url   VARCHAR(500),
    price       BIGINT       NOT NULL,
    likes_count BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id)
    -- 보조 인덱스 없음 (베이스라인)
) ENGINE = InnoDB;
