# 주간/월간 랭킹 배치 & MV 설계

> 완성된 구현물의 코드 레벨 정리는 [`batch-ranking-implementation-summary.md`](./batch-ranking-implementation-summary.md) 참고. 이 문서는 **왜 이렇게 설계했는지**(배경, 대안, 선택 근거)를 정리한다.

## Introduction & Goals

### Context / Background

기존 랭킹 기능은 Redis ZSET 기반 **일간** 랭킹만 제공했다(`GET /api/v1/rankings?date=yyyyMMdd`). 이번 라운드 요구사항은 여기에 **주간/월간 TOP 100 랭킹**을 추가하는 것으로, 다음 세 가지를 요구한다.

1. `product_metrics`(상품 지표)를 읽어 집계하는 Spring Batch Chunk-Oriented Job
2. 집계 결과를 담는 조회 전용 Materialized View(`mv_product_rank_weekly`, `mv_product_rank_monthly`)
3. 기존 Ranking API가 기간(일간/주간/월간)을 받아 분기 조회하도록 확장

기존 `product_metrics`(`commerce-streamer`의 `ProductMetricsEntity`)는 **상품별 누적 카운터**로, PK가 `product_id` 하나뿐이고 날짜 차원이 없다. "이번 주만", "이번 달만" 같은 임의의 기간을 집계하려면 날짜를 1급 차원으로 가진 데이터가 필요했고, 이것이 아래 대안 검토의 출발점이다.

### Goals

- 배치가 `period`(WEEKLY/MONTHLY) + `periodKey`(예: `2026W30`, `202607`) 파라미터만으로 해당 기간의 Top100을 재현 가능하게 계산한다.
- 기존 일간 랭킹(Redis)과 실시간 상품 조회(`product_metrics` 의존 기능)에 영향을 주지 않는다.
- Ranking API는 기존 `date` 쿼리 계약과 하위 호환을 유지하면서 `period`/`periodKey`를 추가로 지원한다.

## Detailed Design

### System Architecture

```
[commerce-streamer]                [commerce-batch]                      [commerce-api]
카탈로그 이벤트                      rankingProductMvJob (3-Step)            GET /api/v1/rankings
  │ CatalogMetricsProcessor           │                                      │
  ▼                                   ▼                                      ▼
product_daily_metrics  ──읽기(기간합산)──▶  mv_product_rank_weekly/monthly  ──읽기──▶  RankingFacade
(상품×일자별 누적)                    (Top100, delete+insert 교체)              (상품정보 결합, 응답)
```

`rankingProductMvJob`은 3-Step 구조다.

```
stagingCleanupStep (Tasklet)
  → aggregateStep (Chunk 50: RepositoryItemReader → ScoreCalculator → Top100Accumulator)
    → publishStep (Tasklet: staging 검증 후 MV 게시)
```

- **Reader**: `ProductDailyMetricsJpaRepository.aggregateByDateRange(startDate, endDate)` — `RankingBatchJobParameters`가 `periodKey`를 ISO 주(월요일 시작)/월 단위로 변환한 날짜 범위를 GROUP BY SUM으로 페이징 조회
- **Processor**: `score = 0.1*viewCount + 0.2*likeCount + 0.6*orderCount`
- **Writer**: min-heap 기반 `RankingTop100Accumulator`가 스트리밍으로 Top100만 유지
- **Publish**: staging 스냅샷을 rank 연속성/중복 검증 후 `mv_product_rank_weekly` 또는 `monthly`에 delete+insert로 교체 적재

#### 배치 트리거 (Jenkins)

| Job | 용도 | cron |
|---|---|---|
| `ranking-batch-weekly-schedule` | 주간 자동 배치 | 매주 월 03:00 (`H 3 * * 1`) |
| `ranking-batch-monthly-schedule` | 월간 자동 배치 | 매월 1일 03:00 (`H 3 1 * *`) |

파이프라인 스테이지:

```
Build commerce-batch  →  Resolve period  →  Seed test data(선택)  →  Run rankingProductMvJob
(bootJar)                (PERIOD_KEY 없으면    (SEED_TEST_DATA=true일 때만:      (job.name=rankingProductMvJob
                           직전 완료 주/월       product_daily_metrics             --period=... --periodKey=...)
                           자동 계산)            더미 데이터 채움)
```

- **`Resolve period`**: `PERIOD_KEY`를 비워두면 "직전에 완료된" 주/월을 자동 계산한다(`RankingBatchJobParameters`와 동일한 ISO 주 규칙, `date +%G`/`+%V`로 ISO 주차 계산). 스케줄 실행은 사람이 매번 값을 넣을 수 없으므로 필수 기능이고, 수동 검증 Job도 값을 비워두면 동일하게 동작한다.
  - 구현 중 Jenkins 선언형 파이프라인의 `environment{}` 블록에 빈 문자열(`params.PERIOD_KEY ?: ''`)을 대입하면 실제로는 `null`로 붕괴되는 현상을 발견했다(선언형 `environment{}`가 빈 문자열 값을 변수 미설정으로 처리하는 것으로 보임). 이 때문에 `PERIOD_KEY`는 `environment{}`에 두지 않고, `Resolve period` 스테이지의 `script{}`에서 `params.PERIOD_KEY`를 직접 읽어 `env.PERIOD_KEY`에 명시적으로 채우는 방식으로 우회했다.
- **`Seed test data`**: `product_daily_metrics`는 원래 `commerce-streamer`의 CDC로 채워지는 테이블이라, 검증 목적일 때만(`SEED_TEST_DATA=true`) Jenkins가 선택된 기간에 맞춰 더미 데이터를 직접 시딩한다(날짜 범위 계산 규칙은 `RankingBatchJobParameters`와 동일). 스케줄 Job은 이 값이 `false`라 실제 CDC 누적 데이터만 집계하며, 매번 더미 데이터로 실데이터를 덮어쓰는 일이 없다.
- **`Run rankingProductMvJob`**: `java -jar commerce-batch-*.jar --job.name=rankingProductMvJob ...`로 원샷 실행하며, datasource는 compose 서비스명(`mysql`, `redis-master`, `redis-readonly`)으로 오버라이드한다. `--spring.jpa.hibernate.ddl-auto=update`로 오버라이드해 `local` 프로파일 기본값(`create`)이 기존 `product_daily_metrics` 데이터를 지우는 것을 막는다.
- **cron은 `Jenkinsfile`이 아니라 Job 생성 스크립트에서 Job별로 부여**한다. 선언형 `triggers{}}`를 스크립트 안에 넣으면 이 스크립트를 공유하는 **모든** Job(수동 검증 Job 포함)에 같은 cron이 걸려버리므로, Groovy Script Console로 Job을 만들 때 `hudson.triggers.TimerTrigger`를 Job 객체에 직접 붙이는 방식을 택했다(파라미터 기본값도 같은 방식으로 Job별 오버라이드).

### Data Models

```sql
-- commerce-streamer가 쓰고 commerce-batch가 읽음
CREATE TABLE product_daily_metrics (
    metric_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    order_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (metric_date, product_id),
    INDEX idx_daily_metrics_product (product_id, metric_date)
);

-- 조회 전용 MV: 주간/월간 각각 Top100 스냅샷
CREATE TABLE mv_product_rank_weekly (
    period_key VARCHAR(10) NOT NULL,   -- 예: 2026W30
    product_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    score DOUBLE NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (period_key, product_id),
    UNIQUE KEY uk_mv_weekly_rank (period_key, rank_position)
);

CREATE TABLE mv_product_rank_monthly (
    period_key VARCHAR(6) NOT NULL,    -- 예: 202607
    product_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    score DOUBLE NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (period_key, product_id),
    UNIQUE KEY uk_mv_monthly_rank (period_key, rank_position)
);
```

#### `product_daily_metrics` — 배치 읽기 원본 (commerce-streamer가 쓰고, commerce-batch가 읽음)

| 컬럼 | 타입 | Null | 기본값 | 키/인덱스 | 설명 |
|---|---|---|---|---|---|
| `metric_date` | `DATE` | NOT NULL | — | **PK(1)** | 지표 발생 일자 (일자별 스냅샷 차원) |
| `product_id` | `BIGINT` | NOT NULL | — | **PK(2)**, `idx_daily_metrics_product(1)` | 상품 ID |
| `order_count` | `BIGINT` | NOT NULL | `0` | | 일자별 주문 수 |
| `like_count` | `BIGINT` | NOT NULL | `0` | | 일자별 좋아요 수 |
| `view_count` | `BIGINT` | NOT NULL | `0` | | 일자별 조회 수 |
| `updated_at` | `DATETIME(6)` | NOT NULL | — | | 마지막 upsert 시각 |

#### `mv_product_rank_weekly` — 주간 Top100 MV (조회 전용)

| 컬럼 | 타입 | Null | 키/제약 | 설명 |
|---|---|---|---|---|
| `period_key` | `VARCHAR(10)` | NOT NULL | **PK(1)**, `uk_mv_weekly_rank(1)` | ISO 주차 키 (예: `2026W30`) |
| `product_id` | `BIGINT` | NOT NULL | **PK(2)** | 상품 ID |
| `rank_position` | `INT` | NOT NULL | `uk_mv_weekly_rank(2)` | 순위 (1~100) |
| `score` | `DOUBLE` | NOT NULL | | 점수 (`0.1*view + 0.2*like + 0.6*order`) |
| `updated_at` | `DATETIME(6)` | NOT NULL | | 게시 시각 |

#### `mv_product_rank_monthly` — 월간 Top100 MV (조회 전용)

| 컬럼 | 타입 | Null | 키/제약 | 설명 |
|---|---|---|---|---|
| `period_key` | `VARCHAR(6)` | NOT NULL | **PK(1)**, `uk_mv_monthly_rank(1)` | 월 키 (예: `202607`) |
| `product_id` | `BIGINT` | NOT NULL | **PK(2)** | 상품 ID |
| `rank_position` | `INT` | NOT NULL | `uk_mv_monthly_rank(2)` | 순위 (1~100) |
| `score` | `DOUBLE` | NOT NULL | | 점수 (주간과 동일 산식) |
| `updated_at` | `DATETIME(6)` | NOT NULL | | 게시 시각 |

두 MV는 `period_key` 길이(주간 `VARCHAR(10)` / 월간 `VARCHAR(6)`)만 다르고 구조가 동일하다.

- MV의 PK를 `(period_key, product_id)`로 잡아 "같은 기간에 같은 상품 중복 저장"을 DB 레벨에서 막는다.
- `UNIQUE(period_key, rank_position)`으로 같은 기간 내 순위 중복도 막는다.
- 게시는 staging 테이블(`mv_product_rank_staging`)에서 rank 1..N 연속/productId 중복 없음을 검증한 뒤 delete+insert로 원자적 교체한다 — 배치 재실행 시에도 항상 최신 스냅샷만 남는다.

### API Design

```
GET /api/v1/rankings?date=yyyyMMdd&page=1&size=20                       (기존, 하위호환 — 일간)
GET /api/v1/rankings?period=WEEKLY&periodKey=2026W30&page=1&size=20     (신규 — 주간)
GET /api/v1/rankings?period=MONTHLY&periodKey=202607&page=1&size=20     (신규 — 월간)
```

- `date`와 `period`는 상호 배타(`RankingMvRequestValidator`), `period`를 쓰면 `periodKey`가 반드시 함께 와야 한다.
- `period` 미지정 시 기존과 동일하게 일간(Redis 우선, fallback RDB)으로 동작한다.
- `period=WEEKLY|MONTHLY`인 경우 `RankingMvReadRepository`가 `mv_product_rank_weekly/monthly`를 읽고, 삭제 상품 보정(backfill) 로직은 일간 경로와 공통 메서드(`collectWithBackfill`)를 재사용한다.
- 응답(`RankingPageResponse`)은 `date`/`period`/`periodKey` 3필드를 모두 갖되 요청 종류에 따라 해당 없는 필드는 `null`로 내려 하위 호환을 유지한다.

### Constraints

- **동시 실행 방지(Redis 분산락)**: 동일 `period`+`periodKey` 조합에 대해 `rankingProductMvJob`이 중복 실행되는 것을 막는다. `RankingBatchLockListener`(`JobExecutionListener`)가 `beforeJob`에서 `ranking:batch:lock:{period}:{periodKey}` 키로 락을 시도하고, 획득 실패 시 `IllegalStateException`을 던져 Job을 시작 전에 `FAILED` 처리한다. `afterJob`에서 자신이 획득한 락만 해제한다(token 비교). TTL(30분)을 두어 락 소유자가 unlock 없이 죽어도(JVM 강제 종료 등) 자동 해제된다.
  - 포트: `domain/ranking/batch/RankingBatchLock`
  - 구현: `infrastructure/ranking/lock/RedisRankingBatchLock` (`SET NX PX` + Lua compare-and-delete)
  - 배선: `batch/job/ranking/RankingBatchLockListener` → `RankingBatchJobConfig`에 첫 번째 listener로 등록

## Alternatives Considered

| 옵션 | Pros | Cons |
|------|------|------|
| A: `product_metrics` 유지 | 신규 테이블/스키마 불필요, `commerce-streamer` 변경 최소화. 상품별 단일 카운터라 조회 로직이 단순 | PK가 `product_id`뿐이라 날짜 차원이 없음 → 임의의 주/월 구간만 골라 집계하는 것이 불가능(누적값만 조회 가능, 특정 시점 스냅샷이 없어 구간 차감도 불가능). 결국 기간별 집계를 하려면 별도 스냅샷 테이블을 또 만들어야 해 근본 문제가 해결되지 않음 |
| **선택: B. `product_daily_metrics` 신규 추가** | PK `(metric_date, product_id)`로 일자별 스냅샷을 유지 → 임의의 `[startDate, endDate]` 구간을 `GROUP BY SUM`으로 집계 가능(주간/월간 동일 로직 재사용). `product_metrics`는 그대로 두어 기존 실시간 조회(상품 상세 등) 영향 없음. 배치 재집계 시에도 원본(일자별 원본 데이터)이 보존돼 있어 안전 | `commerce-streamer`가 이벤트마다 upsert를 하나 더 수행(`product_metrics` 옆에 같은 트랜잭션으로 `product_daily_metrics`도 기록). 상품 수 × 날짜 수만큼 행이 계속 늘어나며, retention 정책이 아직 없음(Constraints 참고) |

**선택 근거:**

`product_metrics`는 PK가 `product_id` 하나뿐이라 상품별 누적치만 알 수 있고, "이번 주"나 "이번 달"처럼 특정 기간에 발생한 지표만 떼어낼 방법이 없다(과거 시점의 스냅샷이 없으므로 구간 차감도 불가능하다). 배치는 `periodKey`로 받은 임의의 기간을 매번 다르게 집계해야 하므로, 날짜를 1급 차원으로 가진 데이터가 필요했다.

`product_metrics` 자체에 날짜 컬럼을 추가해 PK를 확장하는 방법도 검토했으나, 이 테이블은 상품 상세 등 다른 기능이 이미 "현재 누적치"를 읽는 용도로 쓰고 있어 스키마를 바꾸면 그 소비자들에게 영향이 간다. 따라서 `product_metrics`는 손대지 않고, 배치 전용 읽기 원본으로 `product_daily_metrics`를 별도로 두는 쪽을 택했다. `commerce-streamer`가 이벤트 처리 시 두 테이블에 각각 upsert하는 비용(쓰기 1회 추가)은 감수 가능한 수준으로 판단했다.
