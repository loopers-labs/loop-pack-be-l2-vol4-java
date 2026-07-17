# Round 9 Ranking 벤치마크 실행 가이드

## 목적과 해석 범위

이번 실험은 세 가지 질문을 분리해서 측정한다.

1. 같은 이벤트 수를 처리할 때 배치 내부 집계가 실제 Redis 갱신 수와 처리량을 얼마나 바꾸는가?
2. 부분 Redis 성공 뒤 동일 Kafka 배치를 재처리하면 점수와 Top 20이 얼마나 달라지는가?
3. ZSET cardinality와 응답 크기가 Redis 조회 및 전체 Ranking HTTP 응답 시간에 어떤 영향을 주는가?

스트리머 처리량은 `CatalogRankingEventProcessor` 호출부터 실제 `RedisRankingScoreWriter`의 Lua 완료까지다. Kafka broker, fetch 대기, 역직렬화, consumer scheduling, ACK, lag drain은 포함하지 않으므로 Kafka E2E 처리량으로 해석하지 않는다.

API의 `redis-repository`는 ZSET 조회와 tuple 변환을, `full-http`는 RANDOM_PORT에 대한 HTTP 요청부터 응답 body 수신까지를 측정한다. 모두 로컬 same-JVM/Testcontainers 상대 비교이며 운영 용량 보증값이 아니다.

Redis ZSET에는 설정한 cardinality 전체를 넣지만 MySQL에는 조회 가능한 상위 `max(pageSizes)`개 상품만 넣는다. 예를 들어 page size가 `20,100`이면 DB 상품 수는 항상 100개다. 따라서 ZSET 크기 변화와 DB 테이블 크기 변화를 섞지 않고, 실제 반환되는 상품·브랜드 조합 경로는 그대로 측정한다.

## 스트리머 배치 집계와 재시도 오차

빠른 로컬 측정:

```bash
./gradlew :apps:commerce-streamer:rankingBenchmark \
  -PrankingBenchmarkEvents=10000 \
  -PrankingBenchmarkBatchSizes=1,100,1000,3000 \
  -PrankingBenchmarkHotCardinality=100 \
  -PrankingBenchmarkUniformCardinality=10000 \
  -PrankingBenchmarkRuns=2 \
  -PrankingBenchmarkWarmupEvents=1000 \
  -PrankingBenchmarkRetryBatchSizes=50,500,3000 \
  -PrankingBenchmarkFailurePoints=0,25,75 \
  -PrankingBenchmarkLabel=local-10k
```

제출용 10만 이벤트 프로필은 `rankingBenchmarkEvents=100000`, `rankingBenchmarkUniformCardinality=100000`, `rankingBenchmarkRuns=5`로 확장한다.

- Hot 분포는 낮은 상품 cardinality에 round-robin으로 이벤트를 집중한다.
- Uniform 분포는 높은 cardinality로 이벤트를 분산한다.
- warmup은 JIT, Lettuce 연결, Redis Lua 로딩 영향을 측정 전에 제거한다.
- run마다 batch 실행 순서를 회전시킨다.
- setup, 전용 키 삭제, workload 생성, 정확성 검증, 보고서 I/O는 timer 밖에 둔다.
- 최종 member 수, 총점, 상품별 점수, 예상 Redis 갱신 수가 맞지 않으면 벤치마크가 실패한다.
- 재시도 실험의 0%는 첫 쓰기 전에, 25%와 75%는 각각 K개 쓰기 성공 후 K+1번째 쓰기 전에 실패한다. 이후 같은 배치를 전체 재처리한다.

결과는 기본적으로 다음 경로에 생성된다.

```text
apps/commerce-streamer/build/reports/ranking-streamer/
  ranking-aggregation-*.csv
  ranking-retry-drift-*.csv
  ranking-streamer-*.md
```

## Ranking API

관리 가능한 로컬 기준선:

```bash
./gradlew :apps:commerce-api:rankingApiBenchmark \
  -PrankingBenchmarkCardinalities=1000,10000 \
  -PrankingBenchmarkPageSizes=20,100 \
  -PrankingBenchmarkConcurrency=1 \
  -PrankingBenchmarkIterations=200 \
  -PrankingBenchmarkWarmup=20 \
  -PrankingBenchmarkRuns=2 \
  -PrankingBenchmarkLabel=local-baseline
```

10만 member까지 포함한 확장 프로필:

```bash
./gradlew :apps:commerce-api:rankingApiBenchmark \
  -PrankingBenchmarkCardinalities=1000,10000,100000 \
  -PrankingBenchmarkPageSizes=20,100 \
  -PrankingBenchmarkConcurrency=1 \
  -PrankingBenchmarkIterations=200 \
  -PrankingBenchmarkWarmup=20 \
  -PrankingBenchmarkRuns=2 \
  -PrankingBenchmarkLabel=local-100k
```

cardinality 기준선은 concurrency 1로 먼저 측정한다. 동시성 영향은 같은 cardinality와 page size를 고정하고 `rankingBenchmarkConcurrency=25` 또는 `100`으로 별도 실행한다. 200개 표본의 p99는 참고값이며, 꼬리 지연 비교가 목적이면 iterations를 1000 이상으로 올린다.

API 결과는 기본적으로 다음 경로에 생성된다.

```text
apps/commerce-api/build/reports/ranking-api/
  *-results.csv
  *-report.md
```

## 비교할 때 고정할 조건

- 같은 Java 버전, Docker CPU·메모리, 실행 중인 다른 컨테이너 수를 유지한다.
- thermal throttling이나 GC가 의심되면 같은 label로 다시 실행하지 말고 새 label을 사용한다.
- `redis:latest` test fixture는 실행 시점에 이미지가 바뀔 수 있다. 보고서의 실제 Redis 버전을 비교하고, 장기 회귀 기준선을 만들 때는 이미지 버전 고정을 검토한다.
- Hot과 Uniform의 절대 처리량 차이는 ZSET cardinality도 함께 달라진 결과다. 집계 효과는 동일 분포 안에서 batch size별로 비교한다.
- p50/p95/p99뿐 아니라 Redis updates/event, 성공/실패 수, 최종 점수 검증 결과를 함께 본다.

## 최종 측정 자료

2026-07-17 최종 프로필은 Streamer 20,000 이벤트/5회, API cardinality 양방향 각 3회, API load 1,000표본/3회로 실행했다. 실제 명령, 집계 방식, 해석과 선택한 raw CSV는 [최종 벤치마크 보고서](03-ranking-benchmark-report.md)에 기록한다.

빌드 디렉터리의 timestamp 결과는 재빌드 시 사라질 수 있으므로, 제출에 사용한 원본은 다음 경로에 고정한다.

```text
docs/week9/benchmark-results/
```

## 이벤트와 배치 전략 비교 확장

현재 Kafka 마이크로배치 `ZINCRBY`와 `product_metric_hourly` 기반 일간 절대값 스냅샷을 별도 프로필로 비교했다. 두 timer의 시작점이 다르므로 system E2E 처리량처럼 직접 비교하지 않는다. 실행 명령, 측정 경계, raw CSV와 hybrid 운영 제안은 [이벤트 랭킹과 배치 스냅샷 비교](04-event-vs-batch-ranking.md)를 따른다.
