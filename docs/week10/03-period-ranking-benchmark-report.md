# 기간 랭킹 벤치마크 결과

## TL;DR

동일한 페이지 단위 트랜잭션 경계에서 실행별 snapshot은 원천 `GROUP BY` 실행을 1천 상품에서
`11회 → 1회`, 1만 상품에서 `101회 → 1회`로 줄였고, 모든 전략·chunk 실행에서 동일한 결과
digest를 만들었다.

처리시간은 규모에 따라 결과가 달랐다.

- 1천 상품: snapshot `208.45ms`, legacy equivalent `94.31ms`로 snapshot이 약 `2.2배` 느림
- 1만 상품: snapshot `1,718.28ms`, legacy equivalent `3,905.17ms`로 snapshot이 약 `56.0%` 짧음

이번 범위에서는 반복 집계 비용이 상품 수와 page 수에 따라 증가하면서 1천과 1만 사이에
교차점이 생겼다. 따라서 snapshot 구조는 일관성과 restart 격리를 제공할 뿐 아니라, cardinality가
커질 때 반복 원천 집계를 피하는 성능상 이점도 확인됐다. 다만 실제 24시간 밀도와 전체 Spring
Batch Job 실행시간으로 일반화하려면 추가 측정이 필요하다.

## 측정 환경과 범위

| 항목 | 값 |
| --- | --- |
| 측정 시각 | 2026-07-24 KST |
| Java | 21.0.11 |
| OS | Mac OS X aarch64 |
| Database | MySQL 8.0.46 Testcontainers |
| 상품 수 | 1천, 1만 |
| 원천 기간 | 7일 |
| 일별 활성 시간 | 1시간 |
| 원천 행 수 | 7천, 7만 |
| 전략 page size | 100 |
| chunk size | 100, 500, 1,000 |
| warmup | 전략 및 각 chunk 조합별 1회 |
| 측정 run | 조합별 5회 |
| 경합 hold | 400ms |

측정 명령은 다음과 같다.

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
  -PperiodRankingBenchmarkLabel=week10-local-20260724-transactional
```

legacy와 snapshot의 score 처리 페이지는 모두 조회·점수 계산·batch write를 하나의 transaction으로
실행한다. snapshot의 준비 `INSERT ... SELECT`는 별도 `READ_COMMITTED` transaction이다.
전략 timer에는 run key 정리, 원천 집계 또는 snapshot 생성, `RankingScoreFormula` 계산, staging
반영이 포함된다. 원천 seed, digest, 리포트 I/O, JobRepository·listener·lock, MV publish는
포함하지 않는다.

초기 auto-commit 측정은 legacy batch INSERT와 snapshot batch UPDATE의 commit 비용이 비대칭이어서
폐기했다. 이 문서와 아래 원본 파일은 transaction 경계를 보정한 재측정 결과만 사용한다.

## 1. 반복 Paging과 Snapshot 비교

| 상품 수 | 전략 | 중앙 실행시간 | 처리량 | 원천 GROUP 쿼리 | staging page |
| ---: | --- | ---: | ---: | ---: | ---: |
| 1천 | legacy equivalent | 94.31ms | 10,604 products/s | 11 | 10 |
| 1천 | snapshot | 208.45ms | 4,797 products/s | 1 | 10 |
| 1만 | legacy equivalent | 3,905.17ms | 2,561 products/s | 101 | 100 |
| 1만 | snapshot | 1,718.28ms | 5,820 products/s | 1 | 100 |

모든 run에서 score와 view/like/sales를 포함한 staging SHA-256 digest가 일치했다.

1천 상품에서는 snapshot 준비와 score update라는 고정 비용이 반복 집계 절감보다 컸다. 1만
상품에서는 legacy의 원천 `GROUP BY`가 101회로 늘면서 snapshot이 약 `2.27배` 높은 처리량을
보였다. 이번 두 점만으로 정확한 교차 cardinality를 확정할 수는 없지만, 반복 paging 비용이
규모에 따라 빠르게 커지는 경향은 확인됐다.

## 2. Chunk size 비교

chunk timer는 미리 생성한 score 0 snapshot을 대상으로 페이지 단위 transaction의 staging 조회,
점수 계산, score update만 측정한다.

| 상품 수 | Chunk | 중앙 실행시간 | 처리량 | page 수 |
| ---: | ---: | ---: | ---: | ---: |
| 1천 | 100 | 186.11ms | 5,373 products/s | 10 |
| 1천 | 500 | 120.00ms | 8,333 products/s | 2 |
| 1천 | 1,000 | 103.17ms | 9,693 products/s | 1 |
| 1만 | 100 | 1,540.84ms | 6,490 products/s | 100 |
| 1만 | 500 | 1,152.84ms | 8,674 products/s | 20 |
| 1만 | 1,000 | 1,080.64ms | 9,254 products/s | 10 |

이번 환경에서는 1,000이 가장 빨랐다.

- 1천 상품: chunk 100 대비 약 `44.6%` 단축
- 1만 상품: chunk 100 대비 약 `29.9%` 단축
- 500 대비 1,000의 추가 단축은 1천에서 약 `14.0%`, 1만에서 약 `6.3%`

동일한 DB transaction 경계는 재현했지만 JobRepository와 ExecutionContext 저장, 실제 reader/writer
구성, 실패 시 rollback 및 restart 비용은 포함하지 않는다. production chunk 100을 즉시 변경하지
않고 실제 Job 전체 측정에서 500과 1,000을 다시 비교한다.

## 3. 격리수준과 원천 UPDATE 경합

snapshot `INSERT ... SELECT`가 끝난 뒤 transaction을 400ms 유지하면서 동일 원천 행의
`UPDATE` 지연을 측정했다.

| Isolation | Snapshot INSERT 중앙값 | 원천 UPDATE 중앙값 |
| --- | ---: | ---: |
| REPEATABLE_READ | 246.66ms | 421.33ms |
| READ_COMMITTED | 201.91ms | 4.53ms |

`REPEATABLE_READ`의 원천 UPDATE는 hold 시간과 유사한 약 421ms를 기다렸고,
`READ_COMMITTED`의 중앙값은 약 4.5ms였다. 이는 prepare Step에 적용한 `READ_COMMITTED`가
`INSERT ... SELECT` 이후 source lock retention을 줄이는 방향과 일치한다.

이 실험은 의도적으로 transaction 겹침을 만든 lock 실험이다. 일반 트래픽의 p95/p99나
격리수준 전체의 우열을 뜻하지 않는다.

## 결정

1. **불변 snapshot 구조와 `READ_COMMITTED`를 유지한다.**
   모든 digest가 일치했고, 반복 원천 집계를 실행당 1회로 제한했으며, 1만 상품에서는 처리시간도
   legacy equivalent보다 짧았다.
2. **production chunk size는 아직 100을 유지한다.**
   하네스에서는 1,000이 가장 빨랐지만 실제 Spring Batch 메타데이터·rollback·restart 비용을
   포함하지 않았다.
3. **다음 실험은 실제 Job 경계와 데이터 밀도를 확장한다.**
   - 실제 Job 전체 시간과 강제 실패 후 restart 시간에서 chunk 100/500/1,000 비교
   - 24 activeHours 및 월간 30일 데이터에서 snapshot 교차점 재확인
   - `(run_key, score DESC, product_id)` 인덱스 유무에 따른 aggregate와 publish 시간 분리

## 원본 결과

- [전략 비교 CSV](benchmark-results/period-ranking-strategy-20260724-140538-week10-local-20260724-transactional.csv)
- [Chunk 비교 CSV](benchmark-results/period-ranking-chunk-20260724-140538-week10-local-20260724-transactional.csv)
- [격리수준 경합 CSV](benchmark-results/period-ranking-contention-20260724-140538-week10-local-20260724-transactional.csv)
- [자동 생성 Raw Report](benchmark-results/period-ranking-20260724-140538-week10-local-20260724-transactional.md)
