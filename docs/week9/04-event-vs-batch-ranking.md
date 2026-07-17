# Round 9 이벤트 랭킹과 배치 스냅샷 비교

## 결론

현재 Kafka 마이크로배치 랭킹은 유지하고, 종료된 날짜(`D-1`)를 배치 스냅샷으로 다시 계산해 보정하는 하이브리드가 적합하다.

- 이벤트 경로는 새 행동을 빠르게 반영한다. 같은 Kafka poll 안의 `(날짜, 상품)`을 합친 뒤 Redis `ZINCRBY`로 누적한다.
- 배치 경로는 `product_metric_hourly`를 기준 데이터로 삼아 SQL `GROUP BY`로 일간 재계산 원점수를 만들고, 임시 ZSET을 완성한 뒤 canonical 키로 원자 교체한다.
- live와 batch 모두 음수·0을 포함한 원점수를 저장하고 API 조회에서만 `score > 0`을 노출한다. 이 경계는 같은 이벤트가 Kafka poll에 나뉘어도 일간 합계가 달라지지 않게 한다.
- 이벤트는 신선도를 얻는 대신 at-least-once 재처리 때 중복 점수가 생길 수 있다. 배치는 실행 주기만큼 늦지만 같은 입력의 재실행과 실패 후 복구가 정확하다.
- 당일 canonical 키를 배치로 교체하면 동시에 들어온 `ZINCRBY`를 잃을 수 있다. Job은 KST 기준 오늘·미래 날짜를 거부하고, 운영에서는 consumer lag 0 확인과 grace period 뒤 `D-1`을 실행한다.

이번 실험은 두 전략의 **시스템 전체 비용**을 직접 겨룬 결과가 아니다. 이벤트 timer는 raw event를 processor가 받아 실제 Redis Lua까지 반영하는 구간이고, 배치 timer는 이벤트를 이미 시간별 RDB row로 materialize한 뒤 시작한다. 배치가 빨라 보이는 부분은 비용이 사라진 것이 아니라 ingestion과 사전 집계로 이동한 결과다.

## 구현 구조

```text
catalog-events
  ├─ ranking consumer
  │    Kafka poll microbatch
  │      → (date, productId) 메모리 합산
  │      → Redis Lua ZINCRBY
  │      → 당일 live ranking
  │
  └─ metrics consumer
       event_handled 중복 확인
         → product_metrics 갱신
         → product_metric_hourly MySQL UPSERT
         → 같은 DB transaction에서 event_handled 기록
                              │
                              └─ external CLI / orchestrator, D-1
                                   dailyRankingSnapshotJob
                                     → SQL GROUP BY product_id
                                     → shared RankingScoreFormula
                                     → temporary ZSET multi-ZADD
                                     → Lua RENAME overwrite
                                     → 종료일 canonical ranking
```

`product_metric_hourly`는 `(metric_date, metric_hour, product_id)` unique key를 사용한다. `event_handled` 확인·메트릭 갱신·처리 완료 기록이 한 transaction에 있으므로 동일 이벤트 재전달의 DB 효과를 한 번으로 제한한다. 여기서 exactly-once는 Kafka 전송 자체가 아니라 **이 애플리케이션의 DB 반영 효과**에 대한 표현이다.

배치 Job은 내부 scheduler를 갖지 않는다. 외부 CLI나 orchestrator가 `dailyRankingSnapshotJob`에 KST 기준 종료된 `requestDate`를 전달해야 한다. Job은 canonical 날짜 기반 Redis 실행 lock을 DB 조회 전에 `SET NX`로 획득하고 publish 이후 token 검증 Lua로 해제해, 같은 날짜의 동시 실행이 stale snapshot을 나중에 덮는 것을 막는다. Reader는 날짜별 row를 상품 단위로 합산하고, 이벤트 경로와 같은 `RankingScoreFormula`로 점수를 계산한다. Publisher는 production에서 500개씩 임시 ZSET에 `ZADD`하고, 전부 성공한 뒤 Lua 안에서 canonical 키를 교체한다.

## 측정 경계

| 전략 | Timer에 포함 | Timer에서 제외 |
| --- | --- | --- |
| 이벤트 | raw 10만 event의 `CatalogRankingEventProcessor` 진입부터 실제 `RedisRankingScoreWriter` Lua 완료 | Kafka broker, fetch wait, 역직렬화, consumer scheduling, ACK, lag drain, metrics DB ingest |
| 배치 | 사전 집계된 RDB row의 SQL `GROUP BY`, 점수 공식, benchmark-equivalent multi-`ZADD`, Lua `RENAME` | 원천 이벤트 ingest와 시간별 UPSERT, scheduler 대기, seed, cleanup, 검증, report I/O |

따라서 `elapsed_ms`를 이용해 “배치가 이벤트보다 몇 배 빠르다”거나 “운영에서 초당 몇 건을 처리한다”고 결론 내리면 안 된다. 이벤트 경로는 매 이벤트의 실시간 materialization 비용을 측정하고, 배치 경로는 이미 materialize된 row로 스냅샷을 만드는 비용만 측정한다. 공정한 system E2E 비교를 하려면 동일한 원천 event가 들어온 시점부터 최종 Redis 상태가 준비될 때까지 Kafka ingest, DB write, scheduler와 lag를 모두 포함해야 한다.

두 경로 모두 same-JVM/Testcontainers에서 실행했다. 상대적인 구조 차이를 관찰하는 실험이지 운영 capacity나 SLO를 보증하는 부하 테스트가 아니다.

## 처리량과 Redis 명령 수

중앙값은 고정한 [원본 CSV](benchmark-results/)에서 동일 조건 5회 값을 다시 계산했다.

### 이벤트 경로: raw event 10만 건, batch 3,000

| 분포 | 상품 수 | 중앙 elapsed | 중앙 events/s | Redis updates | 중앙 processor batch p95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Hot | 100 | 279.5114ms | 357,767.1928 | 3,400 | 15.1613ms |
| Uniform | 100,000 | 6,964.9463ms | 14,357.6124 | 100,000 | 235.8783ms |

Hot은 한 poll 안에서 같은 100개 상품이 반복되어 10만 event가 3,400번의 Redis 갱신으로 줄었다. Uniform은 모든 event가 서로 다른 상품이어서 합칠 대상이 없고 10만 번 모두 `ZINCRBY`했다. 효과를 결정한 것은 batch size 자체가 아니라 `(날짜, 상품)` 반복도다.

표의 batch p95는 각 `process` 호출 시간의 run별 p95를 다시 중앙값으로 낸 값이다. Kafka에서 event가 발생한 뒤 화면에 보이기까지의 E2E freshness가 아니다.

### 배치 경로: 시간별 row에서 일간 스냅샷 생성

| 상품 수 | 측정 chunk | 중앙 elapsed | 중앙 products/s | ZADD chunk 수 |
| ---: | ---: | ---: | ---: | ---: |
| 100 | 100 | 3.6788ms | 27,182.8435 | 1 |
| 100,000 | 100 | 377.9648ms | 264,574.9078 | 1,000 |
| 100,000 | 1,000 | 207.8391ms | 481,141.3635 | 100 |
| 100,000 | 3,000 | 199.5278ms | 501,183.2109 | 34 |

10만 상품에서 chunk를 100→3,000으로 늘리면 Redis round trip이 1,000→34회로 줄면서 스냅샷 생성 구간은 짧아졌다. 다만 이 publisher는 chunk 크기 비교를 위한 **benchmark-equivalent 구현**이다. Production `DailyRankingSnapshotPublisher`의 chunk는 500으로 고정되어 있으므로 표의 처리량을 production 구현의 처리량이라고 주장할 수 없다.

배치 10만 상품 seed의 `metric_units=450,001`은 view/like/sales count delta의 합이며 실제 메시지 수가 아니다. 이 값은 이미 100,000개의 시간별 row로 집계된 상태이므로 이벤트 실험의 raw 10만 건과 입력량·입력 형태가 모두 다르고, elapsed 절대값끼리 직접 비교하지 않는다.

## 재실행 정확성과 장애 복구

### 이벤트 `ZINCRBY`

| Batch | 부분 성공 지점 | 점수 과대 계상 | 오차 상품 | Top 20 set overlap |
| ---: | ---: | ---: | ---: | ---: |
| 3,000 | 0% | 0.0000% | 0 | 100% |
| 3,000 | 75% | 2.2498% | 2,250 | 100% |

일부 aggregate write가 성공한 뒤 Kafka offset을 ACK하지 못하면 동일 batch가 다시 전달되고, 이미 성공한 상품도 `ZINCRBY`된다. 75% 실패 실험에서는 2,250개 상품에 drift가 생겨 총점이 2.2498% 과대 계상됐다.

Top 20 overlap 100%는 **상위 20개 구성원이 같다**는 뜻일 뿐 순서가 같다는 뜻이 아니다. 이 결과만으로 순위 정확성을 주장할 수 없고, 총점 오차가 작거나 Top-N set이 같아도 내부 순서는 달라질 수 있다.

### 배치 절대값 스냅샷

| 상품 수 | Chunk | 같은 입력 재실행 exact | build 실패 시 canonical 불변 | retry 후 exact |
| ---: | ---: | --- | --- | --- |
| 100,000 | 100 | true | true | true |
| 100,000 | 1,000 | true | true | true |
| 100,000 | 3,000 | true | true | true |

배치는 `ZINCRBY`가 아니라 계산이 끝난 일간 원점수를 임시 키에 `ZADD`한다. build 중 실패하면 canonical 키는 건드리지 않고, 재실행이 끝났을 때만 임시 키를 canonical 키로 교체한다. 그래서 동일 입력 재실행 digest, 실패 시 canonical digest, retry 후 digest가 모든 측정 chunk에서 기대값과 같았다.

이 복구 성질은 원천인 `product_metric_hourly`가 정확하고 다시 읽을 수 있다는 전제에 의존한다. 이벤트 중복 방지 비용을 Redis write path가 아니라 MySQL 영속 저장과 unique/upsert로 이동시킨 선택이다.

## 신선도

이벤트 경로의 관측값은 processor batch p95 중앙 `15.1613ms`(Hot), `235.8783ms`(Uniform)다. 하지만 Kafka fetch 설정, 대기, consumer lag가 빠져 있으므로 사용자 관점 freshness는 측정하지 않았다.

배치 freshness CSV는 sleep이나 실제 scheduler로 관측한 분포가 아니다. `activeHours=1`, 10만 상품, chunk 100의 측정 runtime에 `주기 + runtime`을 더한 보수적 파생값이다. 표본은 `n=5`이고 nearest-rank 방식이라 p95는 사실상 관측 최댓값에 가깝다.

| 가정한 실행 주기 | Runtime p50/p95 | 파생 freshness p50/p95 |
| ---: | ---: | ---: |
| 1초 | 377.9648 / 470.1852ms | 1,377.9648 / 1,470.1852ms |
| 10초 | 377.9648 / 470.1852ms | 10,377.9648 / 10,470.1852ms |
| 60초 | 377.9648 / 470.1852ms | 60,377.9648 / 60,470.1852ms |

실제 운영 freshness에는 scheduler jitter, 선행 Job 대기, DB/Redis 네트워크, consumer lag와 grace period가 추가된다. D-1 보정은 초 단위 freshness보다 종료 날짜의 최종 정확성과 복구 가능성이 목적이다.

## 트레이드오프

| 관점 | 이벤트 마이크로배치 | 일간 배치 스냅샷 |
| --- | --- | --- |
| 신선도 | 높음. 다만 실제 Kafka E2E는 별도 측정 필요 | 실행 주기와 grace period만큼 늦음 |
| 재실행/복구 | 부분 성공 후 재전달 시 `ZINCRBY` 중복 가능 | 같은 SOT면 절대값 재실행 exact, build 실패 시 canonical 유지 |
| Redis 명령 | 중복도가 높으면 batch 내 집계로 감소, Uniform이면 event당 1회 | 상품 수/chunk만큼 multi-`ZADD` 후 1회 publish |
| 저장·DB write 비용 | 랭킹 경로 자체는 Redis 중심 | event별 시간 집계 UPSERT와 `event_handled`, 시간별 row 저장 필요 |
| 운영 복잡도 | consumer lag, ACK, poison event, Redis retry 관리 | Job parameter, 외부 scheduler, D-1 cutoff, lag/grace, 실패·재실행 관리 |
| 적합한 용도 | 당일 live 랭킹, 변화가 바로 보여야 하는 화면 | 종료일 canonical 보정, Redis 유실 복구, 감사 가능한 재계산 |

어느 한쪽이 항상 우월한 것이 아니다. 이벤트는 계산을 사건 발생 시점에 분산하고, 배치는 원천 저장 후 특정 시점에 모아서 계산한다. 요구하는 신선도·정확성·복구 시간과 저장 비용이 선택 기준이다.

## 운영 제안: live event + closed-date reconciliation

1. 당일 `ranking:all:yyyyMMdd`는 현재 Kafka 마이크로배치가 계속 `ZINCRBY`한다.
2. `product_metric_hourly`는 event 처리의 멱등 영속 SOT로 유지한다.
3. 자정 이후 외부 scheduler가 전일 consumer lag 0을 확인한다.
4. late event를 위한 grace period 뒤 `dailyRankingSnapshotJob requestDate=D-1`을 실행한다.
5. 늦은 event 가능성이 남아 있으면 lag 0과 grace 이후 같은 날짜를 한 번 더 재실행한다. 절대값 스냅샷이므로 재실행은 안전하다.
6. canonical member 수·총점·Top-N digest를 관측해 event live 결과와 batch 결과의 drift를 기록한다.

poll 경계 결정성의 수정 전후 재현값은 [랭킹 원점수 결정성 재현과 개선](06-ranking-score-determinism.md)에 분리해 기록했다.

당일 snapshot publish는 금지한다. Job이 KST 기준 오늘·미래 날짜를 코드에서 거부하므로 열린 날짜의 실수 실행은 차단한다. 다만 late event 종료 여부까지 코드가 판단하지는 못하므로 consumer lag 0과 grace period는 여전히 운영 정책으로 보장해야 한다. 같은 날짜 batch끼리는 Redis 실행 lock으로 직렬화하지만, 종료일에 잘못 유입되는 event consumer의 `ZINCRBY`까지 이 lock에 참여시키지는 않는다.

## 실행 명령

배치 스냅샷 비교 벤치마크:

```bash
./gradlew :apps:commerce-batch:rankingBatchBenchmark \
  -PrankingBatchBenchmarkCardinalities=100,10000,100000 \
  -PrankingBatchBenchmarkActiveHours=1 \
  -PrankingBatchBenchmarkChunkSizes=100,1000,3000 \
  -PrankingBatchBenchmarkRuns=5 \
  -PrankingBatchBenchmarkWarmup=1 \
  -PrankingBatchBenchmarkFreshnessIntervals=1,10,60 \
  -PrankingBatchBenchmarkOutputDir=build/reports/ranking-batch/measured-v3 \
  -PrankingBatchBenchmarkLabel=local-20260717-batch-v3
```

동일 규모 이벤트 전략 비교:

```bash
./gradlew :apps:commerce-streamer:rankingBenchmark \
  -PrankingBenchmarkEvents=100000 \
  -PrankingBenchmarkBatchSizes=3000 \
  -PrankingBenchmarkHotCardinality=100 \
  -PrankingBenchmarkUniformCardinality=100000 \
  -PrankingBenchmarkRuns=5 \
  -PrankingBenchmarkWarmupEvents=3000 \
  -PrankingBenchmarkRetryBatchSizes=3000 \
  -PrankingBenchmarkFailurePoints=0,75 \
  -PrankingBenchmarkOutputDir=build/reports/ranking-streamer/strategy-comparison \
  -PrankingBenchmarkLabel=local-20260717-event-100k
```

실제 batch Job은 jar를 빌드한 뒤 외부 실행기가 닫힌 날짜를 명시해 호출한다.

```bash
./gradlew :apps:commerce-batch:bootJar

java -jar apps/commerce-batch/build/libs/commerce-batch.jar \
  --job.name=dailyRankingSnapshotJob \
  requestDate=2026-07-16
```

## 한계

- `product_metric_hourly`는 MySQL `ON DUPLICATE KEY UPDATE`에 의존한다. 다른 DB dialect에서는 그대로 동작하지 않는다.
- production/QA의 `hibernate.ddl-auto`는 `none`이고 repository에는 자동 migration runner가 없다. 배포 전에 [운영 적용용 MySQL DDL](sql/01-product-metric-hourly.sql)을 수동 또는 배포 플랫폼 migration으로 반드시 적용해야 한다. 문서의 `DROP TABLE`은 명시적 rollback 참고일 뿐 자동 실행하지 않는다.
- `RENAME` overwrite Lua의 원자성 검증은 standalone Redis 기준이다. Redis Cluster의 hash slot, failover, replication semantics는 검증하지 않았다.
- 동일 날짜 실행 lock은 2시간 lease이며 자동 연장하지 않는다. Job이 lease보다 오래 실행되면 동시 실행 방지가 약해질 수 있으므로 실행 시간 관측과 TTL 조정이 필요하다.
- 실행 lock은 batch Job끼리만 조율하며 event consumer의 종료일 write를 차단하지 않는다.
- same-JVM/Testcontainers 결과라 서버와 부하 생성기가 자원을 공유하고 실제 네트워크가 없다.
- 이벤트의 MySQL ingest, Kafka E2E, scheduler, late-event 분포와 reconciliation 전체 소요 시간은 측정하지 않았다.
- 음수·0 원점수를 유지하므로 사용자에게 노출되지 않는 상품도 TTL 동안 Redis ZSET 메모리를 사용한다.
- batch chunk 비교는 production 알고리즘과 동등한 benchmark publisher를 사용했다. production chunk 500 구현 자체의 처리량은 측정하지 않았다.
- 시간별 SOT의 exactly-once는 `event_handled`와 같은 DB transaction이 유지된다는 애플리케이션 효과 기준이다. 브로커부터 DB까지 분산 transaction을 제공하는 것은 아니다.

## 원본 데이터

- [Batch throughput](benchmark-results/strategy-batch-throughput.csv)
- [Batch recovery](benchmark-results/strategy-batch-recovery.csv)
- [Batch derived freshness](benchmark-results/strategy-batch-freshness.csv)
- [Event throughput 100k](benchmark-results/strategy-event-throughput-100k.csv)
- [Event retry 100k](benchmark-results/strategy-event-retry-100k.csv)

기존 랭킹 구현 자체의 배치 집계·API cardinality 결과는 [03 벤치마크 보고서](03-ranking-benchmark-report.md)에 분리해 두었다. 이 문서는 그 결론을 대체하지 않고 이벤트와 일간 snapshot이라는 전략 비교를 확장한다.
