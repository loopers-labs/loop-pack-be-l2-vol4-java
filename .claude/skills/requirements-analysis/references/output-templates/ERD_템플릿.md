# '<프로젝트명>' ERD

<!-- 사용법: Phase 4 워크플로우의 결과를 본 파일에 누적 저장한다. 매 테이블 정의 마무리 시점에 갱신. -->
<!-- 사전 결정 묶음·양식 가드레일·점검 게이트 등 상세는 references/ERD_작성_가이드.md 참조. -->

본 문서는 [03-class-diagram.md](03-class-diagram.md)의 도메인 모델을 MySQL 8.0+ 물리 스키마로 옮긴 결과를 기록한다. 01·02·03이 도메인 어휘로 "무엇을·왜·누가"를 말한다면, 본 문서는 RDB 어휘로 "그 약속을 어떤 테이블·컬럼·제약으로 보존하는가"를 답한다.

<!-- 서문은 한 문단. 본 라운드 인덱스·FK 정책을 함께 적으면 좋음 (예: "데이터 정합성을 위한 제약(PK·UK)만 명시. 외래 관계는 COMMENT로 표기. 일반 인덱스는 운영 쿼리 패턴이 확인된 후로 미룬다."). -->

## 공통 컨벤션

<!-- 사전 결정 묶음 합의 결과를 컨벤션으로 요약. 운영 컨벤션 문서가 별도 있으면 핵심만. -->

- 엔진: `InnoDB`
- 문자셋 / Collation: `utf8mb4` / `utf8mb4_unicode_ci`
- PK: `BIGINT AUTO_INCREMENT`
- 시간 컬럼: `DATETIME`. 모든 행은 **UTC wall-clock** 시각을 저장한다.
- Soft delete: 모든 테이블이 `BaseEntity`(`modules/jpa`)를 상속받아 `created_at`, `updated_at`, `deleted_at`(nullable) 세 컬럼을 공통 보유한다.

## 다이어그램

<!-- erDiagram 표기 규칙: -->
<!--   컬럼: <TYPE> <column> [PK|UK|FK] "<COMMENT 일부>" -->
<!--   카디널리티: ||--o{ (1:N), ||--|{ (1:1..*), ||--|| (1:1), ||..o{ (약한 참조) -->
<!--   라벨: 동사 (likes·places·owns·receives·contains·appears_in 등) -->
<!--   nullable 컬럼은 COMMENT 설명에 ", nullable" 표기로 일관 -->

```mermaid
erDiagram
    <TABLE_NAME> {
        BIGINT id PK "<테이블 식별자>"
        <TYPE> <column> UK "<한글 설명>"
        <TYPE> <column> "<한글 설명>"
        <TYPE> <column> "<한글 설명>, nullable"
        DATETIME created_at "생성 일시 (UTC)"
        DATETIME updated_at "수정 일시 (UTC)"
        DATETIME deleted_at "삭제 일시 (UTC, nullable)"
    }

    <TABLE_A> ||--o{ <TABLE_B> : <라벨>
```

## 테이블 정의

<!-- 도메인 진행 순서는 01-requirements.md / 03-class-diagram.md와 일치. -->
<!-- 모든 컬럼·테이블에 한국어 COMMENT. 사용자가 비고 단락 정리를 원할 수 있으므로 결정 근거는 본문에 길게 적지 않음 — 근거는 01·03에 있음. -->

### `<table>` — <한글 설명>

```sql
CREATE TABLE <table> (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '<식별자>',
    <column>    <TYPE>       NOT NULL COMMENT '<한글 설명>',
    <column>    <TYPE>       NULL     COMMENT '<한글 설명>',
    created_at  DATETIME     NOT NULL COMMENT '생성 일시 (UTC)',
    updated_at  DATETIME     NOT NULL COMMENT '수정 일시 (UTC)',
    deleted_at  DATETIME     NULL     COMMENT '삭제 일시 (UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_<table>_<col> (<col>)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='<한글 테이블 설명>';
```

<!-- FK 제약을 두지 않는 정책이면 CONSTRAINT fk_... 라인은 만들지 않음. 외래 컬럼은 COMMENT에 `(대상 테이블.id)` 명시. -->
<!-- 같은 Aggregate 내부에서만 FK를 두는 정책이면 그 경계 안의 FK만 명시. -->
<!-- 일반 인덱스는 사전 결정에서 본 라운드 미적용이라면 작성 안 함. -->
