# Round 10 — 주간/월간 랭킹 배치 구현 요약

> 설계 배경/의사결정 과정은 [`batch-ranking-mv-design.md`](./batch-ranking-mv-design.md), 작업 이력은 [`batch-ranking-progress.md`](./batch-ranking-progress.md) 참고. 이 문서는 **완성된 구현물이 실제로 어떻게 생겼는지**를 정리한 결과물 요약이다.

## 1. 한 문장 요약

`commerce-streamer`가 이벤트를 상품×일자별로 쌓아두면(`product_daily_metrics`), `commerce-batch`가 주기적으로 그 중 한 기간(주/월)만 집계해 Top100을 뽑아 MV 테이블에 게시하고, `commerce-api`가 그 MV를 읽어 `GET /api/v1/rankings?period=WEEKLY|MONTHLY&periodKey=...`로 내려준다.

## 2. 데이터 흐름

```
[commerce-streamer]                [commerce-batch]                      [commerce-api]
카탈로그 이벤트                      rankingProductMvJob (3-Step)            GET /api/v1/rankings
  │ CatalogMetricsProcessor           │                                      │
  ▼                                   ▼                                      ▼
product_daily_metrics  ──읽기(기간합산)──▶  mv_product_rank_weekly/monthly  ──읽기──▶  RankingFacade
(상품×일자별 누적)                    (Top100, delete+insert 교체)              (상품정보 결합, 응답)
```

## 3. 스키마 (신규)

| 파일 | 테이블 | 비고 |
|---|---|---|
| `docs/sql/product-daily-metrics.sql` | `product_daily_metrics` | PK `(metric_date, product_id)`. `commerce-streamer`가 쓰고 `commerce-batch`가 읽음 |
| `docs/sql/mv-product-rank.sql` | `mv_product_rank_weekly` | PK `(period_key, product_id)` + `UNIQUE(period_key, rank_position)` |
| 〃 | `mv_product_rank_monthly` | 위와 동일 구조 |
| 〃 | `mv_product_rank_staging` | PK `(period_type, period_key, product_id)` + `UNIQUE(period_type, period_key, rank_position)` |

PK를 `(period_key, product_id)`로 잡은 이유는 실제 PR419 DDL을 확인해 "같은 기간에 같은 상품 중복 방지"가 진짜 지켜야 할 무결성이라는 게 드러났기 때문(§2.5 참고). `version` 컬럼은 PR419엔 있지만 죽은 코드라 우리 스키마엔 넣지 않음.

## 4. `commerce-streamer` 변경

- **`infrastructure/catalog/ProductDailyMetricsEntity.java` / `ProductDailyMetricsJpaRepository.java`** — `product_metrics`와 동일한 `INSERT ... ON DUPLICATE KEY UPDATE` upsert 패턴. `metric_date`는 `CURDATE()` 기준.
- **`application/catalog/CatalogMetricsProcessor.java`** — 각 이벤트 케이스(주문/좋아요/좋아요취소/조회)마다 `product_metrics` upsert 바로 옆에 `product_daily_metrics` upsert를 같은 트랜잭션(`REQUIRES_NEW`)에서 추가 호출.

## 5. `commerce-batch` 신규 (전부 신규 패키지)

### 5.1 순수 도메인 — `domain/ranking/batch/`, `domain/ranking/mv/`

| 클래스 | 역할 |
|---|---|
| `RankingBatchPeriodType` | `WEEKLY` \| `MONTHLY` |
| `RankingBatchJobParameters` | `period`+`periodKey` 검증 및 `periodKey → [startDate, endDate]` 계산(ISO 주/월) |
| `RankingDailyMetricsAggregate` | GROUP BY SUM 결과 DTO |
| `RankingScoreCandidate` | `(productId, score)` |
| `RankingMvScoreCalculator` | `score = 0.1*view + 0.2*like + 0.6*order` |
| `RankingTop100Accumulator` | min-heap(용량100) 기반 Top100 스트리밍 누적 |
| `RankingStagingRankRow` | `(rank, productId, score)` |
| `RankingStagingSnapshotValidator` | rank 1..N 연속 + productId 중복 없음 검증 |
| `RankingStagingRepository` (포트) | staging 저장/삭제/조회 |
| `ProductRankMvRow` / `ProductRankMvPublishRepository` (포트) | 게시(publish) 대상 행 / MV 교체 포트 |

### 5.2 infrastructure — `infrastructure/ranking/batch/`, `infrastructure/ranking/mv/`

- `ProductDailyMetricsEntity`(읽기 전용) + `ProductDailyMetricsJpaRepository.aggregateByDateRange(...)` — GROUP BY SUM 페이징 쿼리
- `MvProductRankStagingEntity` + `RankingStagingRepositoryImpl` — 포트 구현
- `MvProductRankWeeklyEntity` / `MvProductRankMonthlyEntity` + `ProductRankMvPublishRepositoryImpl` — delete(`@Modifying`) + insert 트랜잭션 교체

### 5.3 Job/Step 배선 — `batch/job/ranking/`

```
stagingCleanupStep (Tasklet)
  → aggregateStep (Chunk 50: RepositoryItemReader → RankingMvScoreCalculator → Top100Accumulator)
    → publishStep (Tasklet: 검증 후 MV 게시)
```

- `RankingBatchJobConfig` — Job/3-Step 정의, `RunIdIncrementer`
- `RankingBatchJobParametersValidator` — Job 시작 전 파라미터 검증(`JobParametersInvalidException`)
- `RankingBatchJobMetrics` / `RankingBatchJobMetricsListener` — `batch.rank.job.failure.count`(Counter), `batch.rank.job.last.success.epoch`(Gauge)
- `RankingBatchLock`(포트, `domain/ranking/batch/`) / `RedisRankingBatchLock`(구현, `infrastructure/ranking/lock/`) / `RankingBatchLockListener` — 동일 `period`+`periodKey` 동시 실행 방지. `SET NX PX` + token 기반 compare-and-delete unlock, TTL 30분(설계 배경은 [`batch-ranking-mv-design.md`](./batch-ranking-mv-design.md#constraints) 참고)
- `step/RankingStagingCleanupTasklet`, `step/RankingTop100FlushListener`(`@StepScope` accumulator 공유), `step/RankingPublishTasklet`

### 5.4 기타 수정

- `application.yml`에 `spring.batch.job.enabled: false`(test 프로파일)를 추가 — 기존에 있던, `spring.batch.job.name`을 안 주면 일반 `@SpringBootTest`가 "NONE" Job을 못 찾아 실패하던 버그를 겸사겸사 해결(실제 로컬/운영 원샷 실행에는 영향 없음).

## 6. `commerce-api` 신규/변경

### 6.1 신규 — `domain/ranking/`

- `RankingMvPeriod` — `from(String)` 파싱(`CoreException`)
- `RankingMvRequestValidator` — `date`/`period` 상호배타, `period`+`periodKey` 동반, `periodKey` 포맷 검증
- `RankingMvReadRepository`(포트) — `findPage` / `findRank` / `countTotal`

### 6.2 신규 — `infrastructure/ranking/mv/`

- `MvProductRankWeeklyEntity` / `MvProductRankMonthlyEntity`(읽기 전용, `commerce-batch`와 스키마는 같지만 모듈 경계상 별도 클래스) + `RankingMvReadRepositoryImpl`

### 6.3 변경 — `application/ranking/`, `interfaces/api/ranking/`

- `RankingInfo` — `date` 단일 필드에서 `date`/`period`/`periodKey` 3필드로 확장(`ofDate`/`ofPeriod` 팩토리). 기존 `date`만 쓰는 코드는 그대로 동작.
- `RankingFacade` — `RankingMvReadRepository` 의존성 추가, 삭제상품 보정(backfill) 루프를 `collectWithBackfill(...)` 공통 메서드로 추출해 일간(`RankingRepository`)/MV(`RankingMvReadRepository`) 양쪽에서 재사용.
- `RankingV1Controller` — `period`/`periodKey` 쿼리파라미터 추가, `RankingMvRequestValidator`로 검증 후 `period` 유무로 분기.
- `RankingV1Dto.RankingPageResponse` — `period`/`periodKey` 필드 추가(둘 다 null 허용, 기존 `date` 응답과 하위호환).

### 6.4 API 계약

```
GET /api/v1/rankings?date=yyyyMMdd&page=1&size=20                       (기존, 하위호환)
GET /api/v1/rankings?period=WEEKLY&periodKey=2026W15&page=1&size=20     (신규)
GET /api/v1/rankings?period=MONTHLY&periodKey=202607&page=1&size=20     (신규)
```

`docs/sql`와 별개로 `http/commerce-api/ranking-v1.http`에 성공/실패 케이스 요청 예시 추가.

## 7. 검증한 것

### 7.1 자동화 테스트 (전부 통과)

| 위치 | 테스트 |
|---|---|
| `commerce-streamer` | `ProductDailyMetricsJpaRepositoryIntegrationTest`, `CatalogMetricsProcessorTest`(보강) |
| `commerce-batch` (도메인) | `RankingMvScoreCalculatorTest`, `RankingTop100AccumulatorTest`, `RankingStagingSnapshotValidatorTest`, `RankingBatchJobParametersTest`(ISO 주 경계 포함) |
| `commerce-batch` (infra) | `ProductDailyMetricsJpaRepositoryIntegrationTest`, `RankingStagingRepositoryImplIntegrationTest`, `ProductRankMvPublishRepositoryImplIntegrationTest`, `RedisRankingBatchLockIntegrationTest`(tryLock/unlock, token 불일치 시 해제 무시) |
| `commerce-batch` (Job) | `RankingBatchJobParametersValidatorTest`, `RankingBatchJobMetricsTest`, `RankingBatchLockListenerTest`(락 획득 실패 시 예외, 미획득 시 unlock 미호출), `RankingBatchJobE2ETest`(WEEKLY/MONTHLY 정상, 빈 기간, 재실행 교체, 잘못된 파라미터, 동시 실행 시 락으로 FAILED) |
| `commerce-api` | `RankingMvPeriodTest`, `RankingMvRequestValidatorTest`, `RankingMvReadRepositoryImplIntegrationTest`, `RankingFacadeUnitTest`(보강), `RankingV1ApiE2ETest`(기존 date 회귀 + 신규 week/month 계약) |

### 7.2 실제 로컬 인프라 원샷 실행 (수동 검증)

`docker-compose`로 띄운 실제 MySQL에 대해 `./gradlew :apps:commerce-batch:bootRun --args='--job.name=rankingProductMvJob period=WEEKLY periodKey=2026W29'`를 직접 실행:

- 빈 데이터 상태에서 스키마 자동 생성 + `COMPLETED` 확인
- 5개 시드로 손계산한 점수와 실제 결과 일치(`0.1*view+0.2*like+0.6*order`, 날짜 범위 밖 데이터 정상 제외) 확인
- **1000개 상품 시드**로 WEEKLY/MONTHLY 둘 다 실행 → `mv_product_rank_weekly/monthly` 각각 정확히 100행, productId 중복 없음, rank 1~100 연속, 동점 시 productId 오름차순 타이브레이크까지 실제 확인

### 7.3 Jenkins 파이프라인/스케줄러 실행 검증

`docker/infra-compose.yml`의 `jenkins` 서비스로 실제 로컬 Jenkins를 띄우고, Job 3개(`ranking-batch-verify`/`ranking-batch-weekly-schedule`/`ranking-batch-monthly-schedule`)를 Script Console로 생성한 뒤 각각 수동 트리거로 검증(자세한 구성은 [`batch-ranking-mv-design.md`](./batch-ranking-mv-design.md#배치-트리거-jenkins) 참고).

- `ranking-batch-verify`(`PERIOD_KEY=2026W30`, `SEED_TEST_DATA=true`): Build → Seed → Run 전체 스테이지 성공, `mv_product_rank_weekly`에 30건 insert 및 Job `COMPLETED` 확인
- `ranking-batch-weekly-schedule`를 `PERIOD_KEY` 공백으로 트리거 → `Resolve period` 스테이지가 직전 완료 주(`2026W29`)를 정확히 자동 계산해 `COMPLETED`
- `ranking-batch-monthly-schedule`를 `PERIOD_KEY` 공백으로 트리거 → 직전 완료 월(`202606`)을 정확히 자동 계산해 `COMPLETED`
- 두 스케줄 Job 모두 cron(`H 3 * * 1` / `H 3 1 * *`)이 `config.xml`에 정상 저장된 것을 확인(다음 자동 실행은 컨테이너가 떠 있는 한 매주 월요일/매월 1일 새벽 3시)

## 8. 알려진 제약 / 다음 라운드로 미룬 것

이번 라운드에서 의도적으로 범위 밖으로 남긴 것들(전부 §5 오픈 이슈에서 이미 결정됨):

- **K8s 스케줄링 없음** — 로컬 Jenkins cron(`ranking-batch-weekly-schedule`/`ranking-batch-monthly-schedule`, 매주 월요일·매월 1일 03:00)으로 자동 실행되도록 붙였지만, 이는 로컬 1회성 검증용 Jenkins일 뿐 운영 스케줄러(K8s CronJob 등)로의 이관은 범위 밖(자세한 내용은 [`batch-ranking-mv-design.md`](./batch-ranking-mv-design.md#배치-트리거-jenkins) 참고)
- **모니터링은 P1(실패 카운트)/P3(마지막 성공 시각)만** — P2(stale snapshot age)는 제외
- **`product_daily_metrics` retention 미구현** — 무한정 쌓이는 테이블, 정리 정책은 추후 과제(§3.3.1)
- **점수 산식이 일간 랭킹과 다름** — 카운트 기반 단순합 vs 일간의 금액 로그 정규화. API/사용자에게 노출하지 않기로 결정됨(§5-4)
