# 기간 랭킹 실측 가이드

## 목적

이 벤치마크는 다음 세 가지 질문에 답하기 위한 재현 가능한 로컬 실험이다.

1. 원천 테이블을 페이지마다 다시 집계하는 grouped paging과 실행별 snapshot을 한 번 생성하는 방식의 차이는 어느 정도인가?
2. snapshot 생성 후 staging 점수 계산의 chunk size를 100, 500, 1,000으로 변경하면 처리시간이 어떻게 달라지는가?
3. `INSERT ... SELECT` 직후 트랜잭션을 유지할 때 `REPEATABLE_READ`와 `READ_COMMITTED`에서 원천 UPDATE 지연이 어떻게 관찰되는가?

운영 코드는 변경하지 않는다. 벤치마크 데이터는 2099-03-01부터 시작하는 전용 날짜와
`period-bench:` run key만 사용하며, 해당 범위만 실행 전후에 정리한다.

## 빠른 확인

Docker가 실행 중인 상태에서 아래 명령을 실행한다.

```bash
./gradlew :apps:commerce-batch:periodRankingBenchmark \
  -PperiodRankingBenchmarkCardinalities=100 \
  -PperiodRankingBenchmarkPeriodDays=7 \
  -PperiodRankingBenchmarkActiveHours=1 \
  -PperiodRankingBenchmarkPageSize=25 \
  -PperiodRankingBenchmarkChunkSizes=25,50 \
  -PperiodRankingBenchmarkRuns=1 \
  -PperiodRankingBenchmarkWarmup=0 \
  -PperiodRankingBenchmarkContentionRuns=1 \
  -PperiodRankingBenchmarkHoldMs=100 \
  -PperiodRankingBenchmarkLabel=smoke
```

결과는 기본적으로 `apps/commerce-batch/build/reports/period-ranking`에 생성된다.

- `period-ranking-strategy-*.csv`
- `period-ranking-chunk-*.csv`
- `period-ranking-contention-*.csv`
- `period-ranking-*.md`

## 제출용 측정

로컬 자원이 허용하면 상품 수를 1천, 1만으로 측정한다. 실행 순서 편향을 줄이기 위해
전략, chunk size, 격리수준의 순서는 run마다 회전한다.

```bash
./gradlew :apps:commerce-batch:periodRankingBenchmark \
  -PperiodRankingBenchmarkCardinalities=1000,10000 \
  -PperiodRankingBenchmarkPeriodDays=7 \
  -PperiodRankingBenchmarkActiveHours=1 \
  -PperiodRankingBenchmarkPageSize=100 \
  -PperiodRankingBenchmarkChunkSizes=100,500,1000 \
  -PperiodRankingBenchmarkRuns=5 \
  -PperiodRankingBenchmarkWarmup=1 \
  -PperiodRankingBenchmarkContentionRuns=5 \
  -PperiodRankingBenchmarkHoldMs=400 \
  -PperiodRankingBenchmarkLabel=week10-submission
```

월간 규모를 확인할 때는 `periodDays=30`을 사용한다. 상품 수와 기간을 동시에 크게 올리면
seed row가 `상품 수 × 기간 × activeHours`로 증가하므로 먼저 1천 상품으로 소요시간을 확인한다.

출력 위치는 다음처럼 변경할 수 있다.

```bash
-PperiodRankingBenchmarkOutputDir=/absolute/path/to/results
```

## 측정 경계

전략 timer에 포함되는 작업은 다음과 같다.

- 현재 benchmark `run_key`의 staging 정리 transaction
- legacy의 페이지별 원천 집계·점수 계산·staging INSERT transaction
- snapshot의 별도 `READ_COMMITTED` 준비 transaction과 페이지별 점수 계산·UPDATE transaction

chunk timer는 미리 생성한 score=0 snapshot staging을 대상으로 다음 작업만 포함한다.

- staging keyset paging, shared `RankingScoreFormula` 점수 계산, score UPDATE를 묶은 페이지별 transaction

chunk용 snapshot cleanup과 `INSERT ... SELECT`는 timer 밖이다.

다음 작업은 timer에서 제외한다.

- 원천 seed와 전체 benchmark 정리
- SHA-256 digest 계산과 정합성 assert
- CSV 및 Markdown 작성
- 실제 JobRepository, listener, lock lease, 최종 MV TOP 100 publish

따라서 strategy와 chunk 결과는 **전체 Spring Batch Job 실행시간이나 운영 처리량이 아니다**.
두 집계 파이프라인의 상대적인 DB 작업량과 실행시간을 비교하는 수치다. legacy는 비교를 위해
Spring Batch MySQL grouped paging의 first/remaining page SQL 형태를 재현했으며 현재 운영 구현이 아니다.

## 정합성 검증

각 strategy와 chunk 실행은 staging 전체를 `product_id` 순으로 직렬화해 SHA-256 digest를 계산한다.
digest에는 score와 view/like/sales 집계값이 모두 포함된다.

- 동일 seed의 legacy와 snapshot digest가 다르면 테스트가 실패한다.
- 각 chunk size의 digest가 전략 비교 기준 digest와 다르면 테스트가 실패한다.
- 강한 성능 threshold는 두지 않는다. 로컬 머신과 컨테이너 상태 차이를 기능 실패로 오인하지 않기 위해서다.

## 결과 해석

### Strategy CSV

- `source_group_query_count`: 원천 `GROUP BY` SQL 실행 횟수다.
- `staging_page_count`: 점수 계산 및 staging 반영 페이지 수다.
- legacy의 원천 query 수가 cardinality/page size에 따라 증가하고 snapshot은 1로 유지되는지 확인한다.
- 중앙값 실행시간은 Markdown의 `Median summary`를 사용하고 raw row도 함께 보관한다.

두 방식은 같은 staging schema와 최종 row cardinality를 사용하지만 SQL write 형태는 다르다.
legacy는 계산된 score를 INSERT하고 snapshot은 raw 집계 INSERT 후 score UPDATE를 수행한다.
따라서 결과를 단순한 SQL 한 건당 비용 비교로 해석해서는 안 된다.

### Chunk CSV

- 작은 chunk는 round trip과 commit 후보 횟수가 늘어날 수 있다.
- 큰 chunk는 한 번에 보유하는 객체와 batch parameter가 늘어난다.
- 동일한 DB transaction 경계는 재현하지만 JobRepository·ExecutionContext 저장, 실제
  reader/writer 구성, rollback·restart 비용까지 증명하지 않는다.

### Contention CSV

snapshot `INSERT ... SELECT`가 끝난 직후 transaction을 `holdMs` 동안 유지하고, 같은 원천 row의
UPDATE 지연을 측정한다. 이는 의도적으로 만든 post-INSERT source-lock retention 실험이다.

- `source_update_latency_ms`가 hold 시간에 가까우면 source lock이 transaction 종료까지 유지된 정황이다.
- 이 실험만으로 일반적인 p95/p99, DB capacity, 모든 쿼리 형태의 격리수준 우위를 주장할 수 없다.
- 각 trial 전에 UPDATE 대상 row는 같은 seed 값으로 복원하므로 격리수준 순서에 따라 값이 누적되지는 않는다.
- snapshot 정합성 전체를 증명하는 실험도 아니다. 실제 운영에서는 지연 이벤트 허용 범위와 배치 grace period를 함께 결정해야 한다.

## 재현 시 기록할 정보

Markdown에는 Java, OS, MySQL 버전과 label이 기록된다. 제출 문서에는 다음도 함께 남기는 것이 좋다.

- Docker Desktop에 할당한 CPU와 메모리
- 측정 중 다른 고부하 프로세스 유무
- warmup/run 수
- 사용한 전체 Gradle 명령
- median뿐 아니라 raw CSV

## 이번 실측 결과

2026-07-24에 1천·1만 상품, 7일, 하루 1시간 데이터로 전략과 각 chunk를 한 번씩 예열한 뒤
각 조합을 5회 측정했다.
해석과 결정은 [기간 랭킹 벤치마크 결과](03-period-ranking-benchmark-report.md)에,
자동 생성 보고서와 CSV는 [benchmark-results](benchmark-results)에 보관한다.
