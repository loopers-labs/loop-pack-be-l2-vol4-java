# Phase 4 가이드 — `04-erd.md` 작성 (선택)

본 문서는 Phase 4의 단계별 워크플로우와 양식 가드레일을 담는다. 산출물 골격은 `output-templates/ERD_템플릿.md`에 있다.

## 진입 조건

다음 중 하나에서 가동.

- 사용자가 명시적으로 "ERD 그리자" / "04 작성하자" / "RDB 스키마로 옮기자" / "테이블 정의 작성" 같은 발화
- Phase 3 종료 시 진입 의사 확인 단계에서 사용자 동의
- 기존 `04-erd.md`에 새 도메인을 누적하려 함

진입 시 기존 04가 있으면 Read해 기존 테이블·관계·공통 컨벤션을 메모리에 적재. 03이 완성되어 있어야 효율적이지만 강제는 아니다 (01만 있어도 ERD 작성 가능).

## ERD의 본질 — 도메인 모델의 RDB 옮김

본 04는 **03 도메인 모델(Entity·VO·Aggregate 경계·메시지)을 RDB 물리 스키마로 옮긴 결과**다. 01·02·03이 도메인 어휘로 "무엇을·왜·누가"를 말한다면, 04는 RDB 어휘로 **"그 약속을 어떤 테이블·컬럼·제약으로 보존하는가"**를 답한다. 다음 단계는 구현.

### 03과의 어휘 분담

| 결정 위치 | 어디서 답하는가 |
| --- | --- |
| 도메인 책임·메시지·Aggregate 경계 | 03 |
| 결정 분기 트레이드오프 | 01 |
| 시각화된 흐름 | 02 |
| 테이블·컬럼·키·인덱스·DDL | 04 |

04는 결정 근거를 길게 적지 않는다 — 근거는 01·03에 있다. 04는 **RDB 어휘 자체에 집중**한다.

## 사전 결정 묶음 — Plan 단계에서 한 번에

ERD 작성 전에 다음 큰 결정을 **한 묶음**으로 사용자와 합의한다. AskUserQuestion으로 묶어 묻는다.

### 1. 기존 도메인 재기재 vs 참조

이전 volume의 ERD에 이미 정의된 테이블(예: `users`)을 본 04에 어떻게 둘지.

- **A. 재기재 (자기완결)** — 본 ERD만 봐도 schema 전체가 보임. volume-1 DDL 중복이지만 자기완결성 우선. **권장**.
- **B. 참조만** — 이전 volume ERD를 참조하라는 한 줄만. 중복 회피.

### 2. 도메인 시각과 인프라 시각 분리

같은 의미의 시간을 컬럼 두 개로 두는가, 하나만 두는가.

- **두 컬럼**: `ordered_at` (도메인, "주문 시각") + `created_at` (인프라, 행 생성 시각)
- **한 컬럼**: `created_at`만

도메인이 시간을 별도 의미로 다루면 (주문 시각·결제 시각·발송 시각 등) **두 컬럼 분리 권장**. 03 다이어그램과 일관 유지. 결제 도입 시 갈라지는 자리가 미리 잡힘.

### 3. FK 제약 정책

- **A. RDB FOREIGN KEY 제약** — DB가 참조 정합성 강제. ON DELETE/UPDATE 동작 명시.
- **B. COMMENT로만 표기** — DDL에는 PK·UK만. 컬럼 COMMENT에 `(대상 테이블.id)` 또는 `(FK → 대상 테이블.id)`. 참조 정합성은 애플리케이션 책임.
- **C. 같은 Aggregate 내부만 FK** — Aggregate 경계 안(예: `order_items.order_id`)은 FK, 다른 Aggregate 참조는 COMMENT만.

**권장: B 또는 C**. FK 제약은 운영 시 마이그레이션·삭제·재시도 흐름을 복잡하게 만들 수 있다. 어떤 선택이든 **erDiagram의 관계선·FK 라벨은 유지** — 도메인 관계 자체가 사라진 건 아니므로.

### 4. 컬럼 타입 정책

| 자리 | 권장 | 이유 |
| --- | --- | --- |
| 자연어 본문 (description 등) | `TEXT` | 길이가 도메인 규칙 아닌 자연어. utf8mb4에서 `VARCHAR(1000)`은 4000바이트 → InnoDB row size 압박. |
| 금액·수량 (price·stock·quantity·*_price) | `INT` | 21억 범위로 일반 이커머스 충분. 03의 `Long`은 Java 표기일 뿐. 금액에 보수적 `BIGINT`도 갈래. |
| 상태값 (OrderStatus 등) | `VARCHAR(20)` | MySQL ENUM은 ALTER 어려움. 향후 상태값 추가 여지. |
| 시간 | `DATETIME` | TIMESTAMP의 2038 한계·자동 timezone 변환 회피. UTC wall-clock 저장. |
| 식별자 (PK) | `BIGINT AUTO_INCREMENT` | 가이드 일관. 21억 한계 안전 마진. |

### 5. 일반 인덱스 정책

- **A. 운영 쿼리 패턴 추정해 미리 박기** — `idx_*` 다수.
- **B. 데이터 정합성 제약(PK·UK)만 명시, 일반 인덱스는 운영 후 추가**. **권장** — 미리 박은 인덱스는 INSERT/UPDATE 비용이 들고, 실제 안 쓰이는 경우가 많다.

### 6. 이름 UK 정책

원칙: **01에 명시된 UK만 ERD에 반영**. 01에 "이름이 중복될 수 없다"가 없으면 ERD가 임의로 UK를 강제하지 않는다 (`brands.name` 사례).

---

## 워크플로우

```
1. Plan 단계 — 사전 결정 묶음 합의 (한 번에)
2. ERD 첫 작성 (Mermaid + DDL)
3. 사용자 정정 사이클 (단순화 방향)
4. 점검 게이트 (DDL ↔ Mermaid 정합·COMMENT 일관성)
5. 종료
```

---

## 단계 1. Plan 단계 — 사전 결정 묶음 합의

ERD 첫 작성 전에 위 6개 결정을 정리해 한 번에 묻는다. Plan 작성 도구를 사용할 수 있으면 plan 파일에 결정 묶음을 박제(후속 작업 추적성).

가능하면 두 갈래 병렬 탐색 먼저:
- volume-N 산출물 (01·02·03) 정독해 도메인·테이블·관계·VO 평탄화·결정 분기 추출
- 가이드/이전 volume 본보기 ERD에서 컨벤션 추출

---

## 단계 2. ERD 첫 작성 (Mermaid + DDL)

### 양식

````markdown
# '<프로젝트명>' ERD

<서문 한 문단 — 03과의 어휘 분담 + 본 라운드 정책(인덱스·FK)>

## 공통 컨벤션

- 엔진 / 문자셋 / Collation
- PK 표기법
- 시간 컬럼 정책 (DATETIME, UTC wall-clock)
- BaseEntity 3종 컬럼 (created_at·updated_at·deleted_at)

## 다이어그램

```mermaid
erDiagram
    <TABLE_NAME> {
        <TYPE> <column> PK "<한글 COMMENT>"
        <TYPE> <column> UK "<한글 COMMENT>"
        <TYPE> <column> "<한글 COMMENT>, nullable"
    }

    <TABLE1> ||--o{ <TABLE2> : <라벨>
```

## 테이블 정의

### `<table>` — <한글 설명>

```sql
CREATE TABLE <table> (
    <column> <TYPE> <NULL?> COMMENT '<한글>',
    PRIMARY KEY (id),
    UNIQUE KEY uk_<...> (<...>)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='<한글>';
```
````

### 양식 가드레일

- **테이블명 복수형** — `users`, `brands`, `products`, `likes`, `orders`, `order_items`. MySQL 예약어 회피.
- **모든 컬럼·테이블에 한국어 COMMENT**. 운영 중 schema 들춰볼 때 의미가 바로 보이도록.
- **시간 컬럼은 `DATETIME`**. UTC wall-clock 저장.
- **PK는 `BIGINT AUTO_INCREMENT`**.
- **금액·수량은 `INT`** (사전 결정 4 권장). 일관.
- **자연어 본문은 `TEXT`** (사전 결정 4 권장).
- **상태값은 `VARCHAR(N)`** (사전 결정 4 권장). ENUM 회피.
- **BaseEntity 3종 컬럼** — `created_at`·`updated_at`·`deleted_at`(nullable). 모든 테이블에 일관. hard delete 도메인(예: 좋아요)도 BaseEntity 일관성으로 `deleted_at` 컬럼은 보유하지만 운영상 사용 안 함.
- **인덱스 명명** — UK는 `uk_<table>_<col1>_<col2>`, 일반 인덱스는 `idx_<table>_<col>`.
- **FK 제약 미적용 시** (사전 결정 3-B/C) — 컬럼 COMMENT에 `(대상 테이블.id)` 명시. DDL에 `CONSTRAINT fk_...` 없음. erDiagram 관계선·FK 라벨은 유지.
- **NULL/NOT NULL 명시** — `nullable`은 erDiagram 컬럼 설명에도 표기.

### erDiagram 표기 규칙

- **컬럼 표기**: `<TYPE> <column> [PK|UK|FK] "<COMMENT 일부>"`.
- **관계선 카디널리티**:
  - `||--o{` — 1 → 0..* (대표적 1:N)
  - `||--|{` — 1 → 1..* (필수 종속, 예: Order → OrderItem)
  - `||--||` — 1 → 1
  - `||..o{` — 약한 참조 (스냅샷 참조 등 RDB FK 없는 관계)
- **관계 라벨**: 동사. `likes`·`places`·`owns`·`receives`·`contains`·`appears_in`.

---

## 단계 3. 사용자 정정 사이클 — 단순화 방향

첫 작성 후 사용자가 "이거 본 단계에서 필요한가?" 관점으로 정정을 요청할 가능성이 크다. 미리 안고 작성:

- **비고 단락의 과잉**: 각 테이블 DDL 아래에 결정 근거를 길게 적었다면 제거 요청 가능. 근거는 01·03에 있음.
- **일반 인덱스의 과잉**: 추정 인덱스가 많으면 빼는 방향이 자연스럽다.
- **FK 제약의 과잉**: 본 프로젝트는 COMMENT-only 정책이 흔하다.
- **공통 컨벤션의 과잉**: Hibernate 설정·정수 타입 정책 라인 등은 운영 컨벤션 문서로 옮길 수 있다.

**외과적 정리**: 사용자가 직접 손본 부분은 그대로 두고, 명시 요청한 변경만 처리.

---

## 단계 4. 점검 게이트

| 점검 항목 | 무엇을 보는가 |
| --- | --- |
| **DDL ↔ Mermaid 정합** | erDiagram 컬럼과 CREATE TABLE 컬럼이 같은가. 추가·누락·타입 불일치 없는가 |
| **COMMENT 일관성** | 모든 컬럼·테이블에 한국어 COMMENT 있는가. 같은 의미가 다른 어휘로 적혀 있지 않은가 |
| **NULL 일관성** | erDiagram의 `nullable`과 DDL의 `NULL` 표기가 일치하는가 |
| **테이블명 복수형** | 모든 테이블이 복수형인가. MySQL 예약어와 충돌하지 않는가 |
| **시간 컬럼 타입** | TIMESTAMP가 잘못 등장하지 않았는가. 모두 DATETIME인가 |
| **금액·수량 타입 일관** | 사전 결정 4의 정책(INT)이 모든 자리에 일관 적용되었는가 |
| **BaseEntity 3종 컬럼** | 모든 테이블에 `created_at`·`updated_at`·`deleted_at`가 있는가 |
| **FK 정책 일관** | 사전 결정 3 정책(COMMENT-only 등)이 모든 테이블에 일관 적용되었는가 |
| **인덱스 명명** | `uk_*`·`idx_*` 규칙이 일관되는가 |
| **사용자 의도 보존** | 사용자가 직접 정리한 부분(비고·인덱스·FK 제약)을 임의로 복원하지 않았는가 |
| **외래 컬럼 COMMENT** | FK 제약 없는 경우 컬럼 COMMENT에 대상 테이블 명시가 있는가 |
| **01 결정 미충돌** | 01의 결정 분기(스냅샷·멱등·soft delete 등)와 ERD가 모순되지 않는가 |

---

## 단계 5. 종료

점검 결과 반영 후 최종 위치와 테이블 수 짧게 보고. 설계 산출물 4종(01·02·03·04)이 완성. 다음 단계는 구현 — `tdd-helper` 스킬로 진입할 자리.

---

## Phase 4 특화 Anti-patterns

본문 룰을 어기는 흔한 함정:

- 사전 결정 묶음 합의 없이 곧장 ERD 작성 시작.
- ERD에 결정 근거를 풍부히 적기. (근거는 01·03에)
- 시간 컬럼에 `TIMESTAMP` 사용.
- 금액·수량을 `DOUBLE`/`FLOAT`로.
- 자연어 본문에 임의 `VARCHAR(N)` 추정치.
- 상태값에 `ENUM(...)`.
- MySQL 예약어를 테이블명으로. (복수형 사용)
- 모든 컬럼·테이블 COMMENT 누락.
- 운영 쿼리 패턴 확인 없이 추정 인덱스 다수 미리 박기.
- 01에 명시 없는 UK를 ERD가 강제.
- FK 정책이 테이블마다 들쭉날쭉.
- BaseEntity 3종 컬럼 중 일부 누락. (hard delete 도메인도 `deleted_at` 보유)
- erDiagram 관계선만 두고 FK 라벨·카디널리티 빠뜨림.
- 사용자가 직접 정리한 부분을 임의로 복원.
- ERD를 한 번에 통째로 다시 쓰기. (부분 갱신, 외과적 변경)
