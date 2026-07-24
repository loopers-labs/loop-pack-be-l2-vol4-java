# Week 10 Implementation Plan: 주간/월간 랭킹 Batch

본 문서는 `/docs/week2` 설계 변경과 `/docs/week10/decisions`의 결정사항을 구현하기 위한 계획서입니다. 모든 구현은 TDD 원칙에 따라 **Red -> Green -> Refactor** 순서로 진행하며, 구조 변경과 행동 변경은 Tidy First 원칙에 따라 분리합니다.

## 1. 목표와 범위

### 목표
- `product_metrics`를 `(metric_date, product_id)` 기준 일자별 메트릭 테이블로 전환한다.
- `MetricsKafkaConsumer`는 이벤트 발생일 기준으로 하루치 메트릭과 `daily_ranking_score`를 누적한다.
- Spring Batch Job은 `period`, `startDate`, `endDate` 파라미터로 주간/월간 랭킹 Top 100을 생성한다.
- 주간/월간 랭킹은 `batch_run_id` 기반 Versioned Snapshot 방식으로 적재한다.
- Batch 실패 중에도 기존 active Snapshot을 유지해 API가 빈 랭킹을 노출하지 않도록 한다.
- Ranking API는 `period + startDate + endDate`를 받아 일간/주간/월간 랭킹을 제공한다.

### 범위
- `modules:ranking-contract`: 기간 타입과 기간 검증 정책 추가
- `apps:commerce-streamer`: 일자별 `product_metrics` 갱신, `daily_ranking_score` 누적
- `apps:commerce-batch`: 주간/월간 랭킹 Batch Job, Reader/Processor/Writer, Versioned Snapshot 적재
- `apps:commerce-api`: 기간 기반 Ranking API 확장, 주간/월간 active Snapshot 조회
- `docs/week10`: 구현 계획과 검증 기준

### 비범위
- 일간 랭킹을 DB MV로 새로 만들지 않는다. `DAILY`는 기존 Redis 일간 랭킹을 유지한다.
- 임의 기간 랭킹(`CUSTOM`, rolling 7 days, rolling 30 days)은 지원하지 않는다.
- 주간/월간 MV에 상품명, 브랜드명, 가격 스냅샷을 저장하지 않는다. API에서 상품/브랜드 정보를 조합한다.
- DB FK 제약 조건은 실제로 강제하지 않는다. ERD의 FK 표기는 참조 의도만 나타낸다.

## 2. 확정 설계

- Ranking API 입력은 `period`, `startDate`, `endDate`, `page`, `size`이다.
- `DAILY`: `startDate == endDate`만 허용하고 Redis `ranking:all:{yyyyMMdd}`에서 조회한다.
- `WEEKLY`: 월요일~일요일 7일 범위만 허용하고 `mv_product_rank_weekly`의 active Snapshot에서 조회한다.
- `MONTHLY`: 매월 1일~말일 범위만 허용하고 `mv_product_rank_monthly`의 active Snapshot에서 조회한다.
- `product_metrics`는 `metric_date`, `product_id`, `view_count`, `like_count`, `sales_count`, `order_amount`, `daily_ranking_score`를 가진다.
- `daily_ranking_score`는 `RankingScorePolicy`와 같은 계산식을 이벤트 단위로 적용해 누적한다.
- Batch Job 파라미터는 `period`, `startDate`, `endDate`이다.
- Batch는 현재 논리 삭제된 상품을 제외하고 Top 100만 적재한다.
- `mv_product_rank_weekly/monthly`는 Batch가 적재하는 조회 전용 물리 테이블이다.
- Versioned Snapshot은 `product_rank_batch_runs.id`를 `batch_run_id`로 사용한다.
- 새 Batch 결과는 `is_active=false`로 먼저 INSERT한다.
- Snapshot 검증이 끝난 뒤 짧은 트랜잭션에서 기존 active Snapshot을 비활성화하고 새 Snapshot을 활성화한다.
- Batch 실행 중 실패하면 기존 active Snapshot은 그대로 유지된다.
- active Snapshot이 아직 없으면 API는 빈 페이지를 반환한다.
- 오래된 Snapshot과 실패한 `batch_run_id` 결과를 정리하는 보관/삭제 정책은 후속 운영 작업으로 둔다.

## 3. 현재 코드와의 차이

- 현재 `ProductMetrics`는 `product_id` unique 기반 누적 테이블이다.
- 현재 `MetricsUpdateService`는 조회/좋아요/판매량만 누적하고 `daily_ranking_score`를 저장하지 않는다.
- 현재 `RankingFacade`와 `RankingV1Controller`는 `date` 또는 `dateKey` 기반 일간 Redis 조회만 제공한다.
- `apps:commerce-batch`에는 demo Job만 있고, 실제 랭킹 집계 Job은 없다.
- 주간/월간 MV 테이블과 `product_rank_batch_runs` 실행 이력 테이블은 아직 없다.

## 4. 단계별 구현 계획

### Step 0. Tidy First: 기간 정책을 공통 계약으로 분리

**목표:** API와 Batch가 같은 `period/startDate/endDate` 검증 정책을 사용하도록 순수 로직을 먼저 만든다.

1. **Red**
   - `modules:ranking-contract`에 `RankingPeriodPolicyTest`를 작성한다.
   - `DAILY`는 `startDate == endDate`가 아니면 실패하는지 검증한다.
   - `WEEKLY`는 월요일~일요일 7일이 아니면 실패하는지 검증한다.
   - `MONTHLY`는 1일~말일이 아니면 실패하는지 검증한다.
2. **Green**
   - `RankingPeriod`, `RankingDateRange`, `RankingPeriodPolicy`를 최소 구현한다.
   - 날짜 파싱은 `yyyyMMdd`만 허용한다.
3. **Refactor**
   - 예외 메시지를 API/Batch에서 재사용하기 쉬운 형태로 정리한다.
   - Redis Key 정책, Score 정책과 의존성이 섞이지 않도록 기간 정책만 분리한다.

**검증:** `./gradlew :modules:ranking-contract:test`

### Step 1. ProductMetrics를 일자별 메트릭으로 전환

**목표:** `product_metrics`가 하루치 메트릭과 `daily_ranking_score`를 저장하도록 모델을 바꾼다.

1. **Red**
   - `ProductMetrics` 도메인 테스트를 작성한다.
   - 이벤트 발생일 `2026-07-14`와 상품 `1L`로 생성하면 `metricDate=2026-07-14`, `productId=1L`을 가진다.
   - VIEW 이벤트 반영 시 `viewCount=1`, `dailyRankingScore=0.1`이 되는지 검증한다.
   - LIKE 이벤트 반영 시 `likeCount=1`, `dailyRankingScore=0.2`가 누적되는지 검증한다.
   - ORDER 이벤트 반영 시 `salesCount`, `orderAmount`, `dailyRankingScore`가 함께 누적되는지 검증한다.
2. **Green**
   - `ProductMetrics` 필드를 일자별 구조로 변경한다.
   - `RankingScorePolicy` 계산 결과를 메트릭 메서드의 `scoreDelta`로 전달한다.
   - `product_id` 단독 unique 제약 의도는 제거하고 `(metric_date, product_id)` unique 의도로 바꾼다.
3. **Refactor**
   - 카운트 증가 메서드 이름을 `addView`, `addLike`, `addSales`처럼 도메인 행위 중심으로 정리한다.
   - 구조 변경 커밋과 점수 누적 행동 변경 커밋을 분리한다.

**검증:** `./gradlew :apps:commerce-streamer:test`

### Step 2. MetricsUpdateService를 이벤트 발생일 기준으로 수정

**목표:** Kafka 이벤트를 일자별 `product_metrics` row에 반영한다.

1. **Red**
   - `MetricsUpdateServiceTest`를 수정/추가한다.
   - 같은 상품이라도 발생일이 다르면 서로 다른 `ProductMetrics`를 조회/저장하는지 검증한다.
   - `PRODUCT_DELETED` 이벤트는 `product_metrics`를 변경하지 않는지 검증한다.
   - ORDER 이벤트의 `daily_ranking_score`가 `RankingScorePolicy`와 같은 값인지 검증한다.
2. **Green**
   - `ProductMetricsRepository.findByMetricDateAndProductId(metricDate, productId)`를 추가한다.
   - `MetricsUpdateService`가 `event.occurredAt()`에서 `metricDate`를 계산하도록 한다.
   - `RankingScorePolicy`를 주입해 `dailyRankingScore`를 누적한다.
3. **Refactor**
   - 테스트용 Fake Repository를 일자별 key 기반으로 바꾼다.
   - `MetricsKafkaConsumer`는 메시지 수신/ack 책임만 갖고, 메트릭 계산은 Service에 남긴다.

**검증:** `./gradlew :apps:commerce-streamer:test`

### Step 3. Batch 영속 모델과 Repository 작성

**목표:** `commerce-batch`가 `product_metrics`, 주간/월간 MV, Batch 실행 이력을 읽고 쓸 수 있게 한다.

1. **Red**
   - `ProductRankBatchRunRepositoryTest`를 작성한다.
   - `RUNNING` 실행 이력을 생성하고 `COMPLETED`, `FAILED`로 전환되는지 검증한다.
   - `ProductRankMvRepositoryTest`를 작성한다.
   - inactive Snapshot 저장 후 active 조회에는 노출되지 않는지 검증한다.
   - active 전환 시 같은 기간의 기존 active Snapshot이 비활성화되는지 검증한다.
2. **Green**
   - `apps:commerce-batch`에 Batch 전용 JPA Entity를 둔다.
   - `ProductMetricsBatchEntity`: `product_metrics` 읽기 전용
   - `ProductRankBatchRunEntity`: `product_rank_batch_runs`
   - `ProductRankWeeklyMvEntity`, `ProductRankMonthlyMvEntity`: 주간/월간 Snapshot
   - Repository는 application port와 infrastructure 구현으로 분리한다.
3. **Refactor**
   - weekly/monthly 공통 Snapshot 저장 로직은 중복을 줄이되, 테이블 분기는 명시적으로 유지한다.
   - JPA Entity를 `commerce-streamer`와 공유하지 않는다.

**검증:** `./gradlew :apps:commerce-batch:test`

### Step 4. Batch 기간 검증 Step 작성

**목표:** 잘못된 Job Parameter는 집계 전에 실패시킨다.

1. **Red**
   - `ProductRankingAggregationJobParameterTest`를 작성한다.
   - `period` 누락, `startDate` 누락, `endDate` 누락 시 Job이 실패하는지 검증한다.
   - `WEEKLY`가 월~일이 아니면 실패하는지 검증한다.
   - `MONTHLY`가 1일~말일이 아니면 실패하는지 검증한다.
2. **Green**
   - `validateRankingPeriodStep` Tasklet을 구현한다.
   - `RankingPeriodPolicy`를 사용해 파라미터 검증을 수행한다.
3. **Refactor**
   - Job Parameter 파싱과 기간 정책 검증을 분리한다.
   - Batch 예외 메시지를 운영자가 이해할 수 있게 정리한다.

**검증:** `./gradlew :apps:commerce-batch:test`

### Step 5. Chunk-Oriented 집계 Step 작성

**목표:** 기간 내 `product_metrics`를 Chunk로 읽고 상품별 점수를 합산해 Top 100 Snapshot을 만든다.

1. **Red**
   - `ProductRankAggregationProcessorTest`를 작성한다.
   - 여러 날짜의 같은 상품 점수가 합산되는지 검증한다.
   - 현재 논리 삭제된 상품은 결과에서 제외되는지 검증한다.
   - score 내림차순으로 rank가 부여되고 Top 100만 남는지 검증한다.
2. **Green**
   - `ProductMetricsItemReader`는 기간 내 `product_metrics`를 Chunk 단위로 읽는다.
   - `ProductRankAggregationProcessor`는 `productId`별 `dailyRankingScore`를 합산한다.
   - 활성 상품 조회 port를 통해 삭제 상품을 제외한다.
   - `ProductRankMvWriter.saveSnapshot(batchRunId, rankings)`는 `is_active=false`로 저장한다.
3. **Refactor**
   - Reader는 DB 페이징/커서 책임만 갖게 유지한다.
   - Processor는 점수 합산과 Top N 선정만 갖고, active 전환을 하지 않는다.
   - Writer는 Snapshot 저장만 하고 검증/활성화 Step과 섞지 않는다.

**검증:** `./gradlew :apps:commerce-batch:test`

### Step 6. Snapshot 검증 및 active 전환 Step 작성

**목표:** 새 Snapshot이 완전히 만들어진 경우에만 API 조회 대상 active 버전으로 전환한다.

1. **Red**
   - `ProductRankSnapshotActivationTest`를 작성한다.
   - 새 Snapshot에 rank 중복이 있으면 active 전환이 실패하는지 검증한다.
   - 새 Snapshot에 같은 product 중복이 있으면 active 전환이 실패하는지 검증한다.
   - 검증 실패 시 기존 active Snapshot이 유지되는지 검증한다.
   - 검증 성공 시 기존 active는 false, 새 Snapshot은 true가 되는지 검증한다.
2. **Green**
   - `validateSnapshotStep`을 구현한다.
   - `activateSnapshotStep`은 짧은 트랜잭션에서 기존 active 비활성화와 새 Snapshot 활성화를 수행한다.
   - 성공 시 `product_rank_batch_runs.status=COMPLETED`, 실패 시 `FAILED`로 기록한다.
3. **Refactor**
   - active 전환 트랜잭션 범위를 최소화한다.
   - Snapshot 검증 기준을 Repository 구현에 숨기지 않고 명시적 메서드로 드러낸다.

**검증:** `./gradlew :apps:commerce-batch:test`

### Step 7. ProductRankingAggregationJob E2E 작성

**목표:** 실제 Job Parameter로 주간/월간 Batch가 끝까지 동작하는지 확인한다.

1. **Red**
   - `ProductRankingAggregationJobE2ETest`를 작성한다.
   - 주간 기간의 `product_metrics` fixture를 넣고 Job 실행 후 active weekly Snapshot Top 100이 생성되는지 검증한다.
   - 월간 기간의 fixture를 넣고 active monthly Snapshot이 생성되는지 검증한다.
   - 기존 active Snapshot이 있는 상태에서 새 실행이 실패하면 기존 active Snapshot이 유지되는지 검증한다.
2. **Green**
   - `ProductRankingAggregationJobConfig`를 작성한다.
   - Step 순서:
     - `validateRankingPeriodStep`
     - `createBatchRunStep`
     - `aggregateAndInsertSnapshotStep`
     - `validateSnapshotStep`
     - `activateSnapshotStep`
   - Job name은 `productRankingAggregationJob`으로 둔다.
3. **Refactor**
   - demo Job과 실제 Job의 공통 listener 사용만 유지하고, demo 코드를 건드리지 않는다.
   - Step 이름과 Bean 이름을 운영 로그에서 구분 가능하게 정리한다.

**검증:** `./gradlew :apps:commerce-batch:test`

### Step 8. Ranking API를 period 기반으로 확장

**목표:** `GET /api/v1/rankings`가 일간/주간/월간 랭킹을 같은 API에서 제공한다.

1. **Red**
   - `RankingV1ControllerTest`를 수정/추가한다.
   - `DAILY`에서 `startDate != endDate`이면 `BAD_REQUEST`가 반환되는지 검증한다.
   - `WEEKLY`가 월~일이 아니면 `BAD_REQUEST`가 반환되는지 검증한다.
   - `MONTHLY`가 1일~말일이 아니면 `BAD_REQUEST`가 반환되는지 검증한다.
   - `DAILY`는 기존 Redis Repository를 조회하는지 검증한다.
   - `WEEKLY/MONTHLY`는 active MV Repository를 조회하는지 검증한다.
   - active Snapshot이 없으면 빈 페이지를 반환하는지 검증한다.
2. **Green**
   - API Request DTO에 `period`, `startDate`, `endDate`를 추가한다.
   - `RankingFacade.getRankings(period, startDate, endDate, page, size)`로 시그니처를 변경한다.
   - `DAILY`는 Redis 조회를 유지한다.
   - `WEEKLY/MONTHLY`는 `ProductRankMvRepository.findActivePage(...)`로 조회한다.
   - 상품/브랜드 정보 aggregation은 기존 RankingFacade 흐름을 재사용한다.
3. **Refactor**
   - API DTO와 Application DTO를 분리한다.
   - period 분기는 Facade 상단에서 명확히 하되, 상품 정보 조합 로직은 중복하지 않는다.

**검증:** `./gradlew :apps:commerce-api:test`

### Step 9. API용 주간/월간 MV Repository 작성

**목표:** `commerce-api`가 active Snapshot만 조회할 수 있게 한다.

1. **Red**
   - `ProductRankMvRepositoryTest`를 작성한다.
   - inactive Snapshot은 API 조회 결과에 포함되지 않는지 검증한다.
   - active Snapshot만 `rank_no` 오름차순으로 페이지 조회되는지 검증한다.
   - weekly/monthly 테이블 분기가 올바른지 검증한다.
2. **Green**
   - `commerce-api`에 조회 전용 MV JPA Entity를 둔다.
   - Application port `ProductRankMvRepository`를 추가한다.
   - Infrastructure 구현체는 `period`에 따라 weekly/monthly JPA Repository를 선택한다.
3. **Refactor**
   - Batch용 Entity와 API용 Entity를 공유하지 않는다.
   - 조회 전용 Repository는 save/update 메서드를 노출하지 않는다.

**검증:** `./gradlew :apps:commerce-api:test`

### Step 10. 통합 검증 및 회귀 테스트

**목표:** streamer -> batch -> api 흐름이 설계대로 이어지는지 확인한다.

1. **Red**
   - 가능한 경우 `tests:commerce-e2e`에 시나리오 테스트를 추가한다.
   - 이벤트로 `product_metrics`가 일자별로 쌓인 뒤 Batch 실행, API 조회까지 연결되는지 검증한다.
2. **Green**
   - 필요한 test fixture를 최소로 추가한다.
   - E2E가 과도하게 무거우면 batch/api 통합 테스트로 범위를 줄인다.
3. **Refactor**
   - 느린 테스트는 E2E 모듈로 분리하고, 단위/통합 테스트는 각 app 모듈에 남긴다.
   - 테스트 데이터 생성 중복을 fixture builder로 정리한다.

**검증:**
- `./gradlew :modules:ranking-contract:test`
- `./gradlew :apps:commerce-streamer:test`
- `./gradlew :apps:commerce-batch:test`
- `./gradlew :apps:commerce-api:test`
- 필요 시 `./gradlew :tests:commerce-e2e:test`

## 5. 커밋 분리 계획

- `refactor`: 기간 정책/DTO/Repository port 등 구조 분리
- `feat`: `product_metrics` 일자별 메트릭 및 `daily_ranking_score` 반영
- `feat`: 주간/월간 랭킹 Batch Job 추가
- `feat`: Ranking API period 기반 확장
- `test`: 누락된 경계/실패 시나리오 보강
- `docs`: 구현 중 새로 확인된 설계 결정 반영

구조 변경과 행동 변경은 같은 커밋에 섞지 않는다. 특히 `product_metrics` 모델 변경은 기존 누적 구조를 깨는 변경이므로, 테스트를 먼저 고치고 가장 작은 단위로 Green을 만든 뒤 후속 리팩토링을 분리한다.

## 6. 완료 기준

- `product_metrics`가 이벤트 발생일 기준 일자별 row로 저장된다.
- `daily_ranking_score`가 `RankingScorePolicy`와 같은 계산식으로 누적된다.
- `productRankingAggregationJob`이 `period/startDate/endDate` 파라미터로 주간/월간 Top 100 Snapshot을 생성한다.
- Batch 실패 중 기존 active Snapshot이 유지된다.
- 주간/월간 API 조회는 active Snapshot만 사용한다.
- active Snapshot이 없는 기간은 빈 페이지를 반환한다.
- DAILY 조회는 기존 Redis 일간 랭킹을 유지한다.
- 주요 모듈 테스트가 모두 통과한다.
