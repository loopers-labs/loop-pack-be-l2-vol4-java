# volume-N ERD

본 문서는 volume-N 전체 기능에서 사용되는 RDB 스키마를 누적 관리한다.

## 공통 컨벤션

- 엔진: `InnoDB`
- 문자셋 / Collation: `utf8mb4` / `utf8mb4_unicode_ci`
- PK: `BIGINT AUTO_INCREMENT` (`BaseEntity`로부터 상속)
- 시간 컬럼: `DATETIME`. 모든 행은 **UTC wall-clock** 시각을 저장한다. Hibernate의 `spring.jpa.properties.hibernate.timezone.default_storage=NORMALIZE_UTC` + `hibernate.jdbc.time_zone=UTC` 설정에 의해 보장된다.
- Soft delete: 모든 테이블이 `BaseEntity`(`modules/jpa`)를 상속받아 `created_at`, `updated_at`, `deleted_at`(nullable) 세 컬럼을 공통 보유한다.

## 다이어그램

```mermaid
erDiagram
    {TABLE_NAME_UPPER} {
        BIGINT id PK "..."
    }
```

## 테이블 정의

### `{table_name}` — (한국어 설명)

({볼륨/feature}) 에서 추가.

```sql
CREATE TABLE {table_name} (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '...',
    -- 컬럼들 ...
    created_at  DATETIME     NOT NULL COMMENT '생성 일시 (UTC)',
    updated_at  DATETIME     NOT NULL COMMENT '수정 일시 (UTC)',
    deleted_at  DATETIME     NULL     COMMENT '삭제 일시 (UTC, soft delete. NULL이면 활성)',
    PRIMARY KEY (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='(한국어 설명)';
```

**비고**

<!-- 테이블명 선택 근거(예약어 회피 등), 컬럼 길이 결정 이유 등 -->
