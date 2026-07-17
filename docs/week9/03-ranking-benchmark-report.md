# Round 9 Ranking 벤치마크 결과

## 결론

이번 로컬 측정에서 배치 집계의 효과는 이벤트가 같은 상품에 반복해서 모일 때만 명확했다. Hot 분포에서 batch 1 대비 batch 1,000은 Redis 갱신 수를 이벤트당 `1.0`회에서 `0.1`회로 줄이며 중앙 처리량이 약 `9.83배` 증가했고, batch 3,000은 `0.035`회와 약 `26.59배`를 기록했다. 반대로 모든 이벤트가 서로 다른 상품으로 분산된 Uniform 분포는 어느 batch에서도 이벤트당 Redis 갱신이 `1.0`회였고 처리량도 약 14.7~15.1K events/s 범위에 머물렀다.

Ranking API는 ZSET 1천~10만 member 구간에서 Top 20/100 조회 지연이 cardinality에 따라 단조롭게 악화되지 않았다. Redis repository p95는 양방향 실행 6회 모두 1ms 미만이었고, 전체 HTTP 경로가 지연의 대부분을 차지했다. 10만 member, Top 20 부하에서는 concurrency 25에서 100으로 올릴 때 중앙 처리량이 `1,129.6 → 1,297.2 RPS`로 14.8% 늘어나는 동안 p95는 `40.436 → 154.281ms`로 3.8배 증가했다. 이 로컬 환경에서는 concurrency 100이 포화 구간에 들어갔다는 신호다.

재시도 오차는 배치 크기와 부분 성공 지점에 따라 커졌지만 총점 오차와 Top 20 변동은 일대일로 움직이지 않았다. batch 3,000의 75% 실패 시 점수 과대 계상은 `11.2491%`였지만 Top 20 일치율은 `100%`였다. 반면 batch 500의 75%에서는 과대 계상 `1.9027%`에도 Top 20 일치율이 `25%`였다. 따라서 총점 오차율만으로 순위 품질을 판단할 수 없다.

## 측정 환경과 경계

| 항목 | 값 |
| --- | --- |
| 측정 시각 | 2026-07-17 KST |
| 장비 | Mac OS X, aarch64, 사용 가능 프로세서 10개 |
| Java | 21.0.11 |
| API Redis | Testcontainers Redis 8.6.3 |
| Streamer Redis | `redis:latest` Testcontainers 이미지 |
| Streamer 이벤트 수 | 시나리오당 20,000개, 5회 |
| API cardinality 기준선 | 1천/1만/10만, page 20/100, 각 방향별 3회 |
| API 부하 축 | 10만 member, page 20, 1,000표본, 3회 |

측정 경계는 다음과 같이 분리했다.

- Streamer 처리량은 `CatalogRankingEventProcessor.process` 진입부터 실제 Redis Lua 완료까지다. Kafka broker, fetch 대기, 역직렬화, consumer scheduling, ACK, lag drain은 포함하지 않는다. 따라서 표의 events/s는 Kafka E2E 처리량이 아니다.
- 재시도 실험은 at-least-once 재전달의 효과만 격리하기 위해 결정론적 in-memory writer를 사용했다. 실제 Redis 장애 확률이나 장애 지속시간을 측정한 값이 아니다.
- API의 `redis-repository`는 ZSET range 조회와 tuple 변환, `full-http`는 same-JVM RANDOM_PORT HTTP 요청부터 응답 body 수신까지다. 후자에는 MySQL 상품/브랜드 조합과 직렬화가 포함된다.
- API 실험은 ZSET cardinality 전체를 Redis에 넣되 MySQL에는 최대 page 크기만큼의 상위 상품만 넣었다. ZSET 크기 변화와 DB 테이블 크기 변화를 섞지 않기 위한 조건이다.
- 모든 결과는 단일 로컬 장비와 Testcontainers에서 얻은 상대 비교값이다. 운영 용량이나 SLO 보증값으로 사용할 수 없다.

## 실행 프로필

선택한 raw CSV를 만든 프로필은 다음과 같다. 결과 파일은 빌드 산출물에서 `docs/week9/benchmark-results`로 원본 그대로 복사했다.

```bash
./gradlew :apps:commerce-streamer:rankingBenchmark \
  -PrankingBenchmarkEvents=20000 \
  -PrankingBenchmarkBatchSizes=1,100,1000,3000 \
  -PrankingBenchmarkHotCardinality=100 \
  -PrankingBenchmarkUniformCardinality=20000 \
  -PrankingBenchmarkRuns=5 \
  -PrankingBenchmarkWarmupEvents=1000 \
  -PrankingBenchmarkRetryBatchSizes=50,500,3000 \
  -PrankingBenchmarkFailurePoints=0,25,75 \
  -PrankingBenchmarkOutputDir=apps/commerce-streamer/build/reports/ranking-streamer/measured \
  -PrankingBenchmarkLabel=local-20260717-measured

./gradlew :apps:commerce-api:rankingApiBenchmark \
  -PrankingBenchmarkCardinalities=1000,10000,100000 \
  -PrankingBenchmarkPageSizes=20,100 \
  -PrankingBenchmarkConcurrency=1 \
  -PrankingBenchmarkIterations=300 \
  -PrankingBenchmarkWarmup=100 \
  -PrankingBenchmarkRuns=3 \
  -PrankingBenchmarkOutputDir=build/reports/ranking-api/measured \
  -PrankingBenchmarkLabel=local-20260717-isolated

./gradlew :apps:commerce-api:rankingApiBenchmark \
  -PrankingBenchmarkCardinalities=100000,10000,1000 \
  -PrankingBenchmarkPageSizes=20,100 \
  -PrankingBenchmarkConcurrency=1 \
  -PrankingBenchmarkIterations=300 \
  -PrankingBenchmarkWarmup=100 \
  -PrankingBenchmarkRuns=3 \
  -PrankingBenchmarkOutputDir=build/reports/ranking-api/measured \
  -PrankingBenchmarkLabel=local-20260717-isolated-desc

./gradlew :apps:commerce-api:rankingApiBenchmark \
  -PrankingBenchmarkCardinalities=100000 \
  -PrankingBenchmarkPageSizes=20 \
  -PrankingBenchmarkConcurrency=<1|25|100> \
  -PrankingBenchmarkIterations=1000 \
  -PrankingBenchmarkWarmup=500 \
  -PrankingBenchmarkRuns=3 \
  -PrankingBenchmarkOutputDir=build/reports/ranking-api/measured \
  -PrankingBenchmarkLabel=local-load-c<1|25|100>
```

Streamer warmup은 벤치마크 설정값을 사용해 JIT, Lettuce 연결, Lua 로딩을 측정 전에 준비했다. setup, Redis key 삭제, workload 생성, 정확성 검증, 보고서 I/O는 timer 밖에 두었다.

## 집계 방법

- Streamer 처리량은 동일 `(distribution, batch size)`의 5개 run에서 events/s 중앙값을 사용했다. 갱신 비율은 `redis_updates / events`다.
- API cardinality는 오름차순 실행 3회와 내림차순 실행 3회를 합친 6회에서 최소값, 중앙값, 최대값을 계산했다. 짝수 표본의 중앙값은 정렬한 가운데 두 값의 평균이다.
- API 부하 축은 concurrency별 3회에서 중앙값을 사용했다.
- 각 실행의 success/failure도 확인했으며, 선택한 API raw run은 모두 실패 0건이었다.
- 오름차순과 내림차순을 함께 본 이유는 먼저 실행된 cardinality가 JIT, 캐시, 커넥션 준비 효과를 독점하는 순서 편향을 드러내기 위해서다.

## 1. Streamer 배치 집계

| 분포 | Batch | Redis updates/event | 중앙 events/s | Batch 1 대비 |
| --- | ---: | ---: | ---: | ---: |
| Hot, 100상품 | 1 | 1.000 | 14,320.3 | 1.00배 |
| Hot, 100상품 | 100 | 1.000 | 14,542.3 | 1.02배 |
| Hot, 100상품 | 1,000 | 0.100 | 140,798.1 | 9.83배 |
| Hot, 100상품 | 3,000 | 0.035 | 380,838.7 | 26.59배 |
| Uniform, 20,000상품 | 1 | 1.000 | 15,057.6 | 1.00배 |
| Uniform, 20,000상품 | 100 | 1.000 | 14,972.1 | 0.99배 |
| Uniform, 20,000상품 | 1,000 | 1.000 | 14,721.1 | 0.98배 |
| Uniform, 20,000상품 | 3,000 | 1.000 | 14,943.4 | 0.99배 |

Hot 분포는 100개 상품을 round-robin으로 순회한다. batch 100에는 각 상품이 정확히 한 번씩만 들어오므로 합칠 중복이 없고, Redis 호출도 100번 발생한다. 그래서 batch 100 자체에는 처리량 이점이 없었다. batch 1,000부터 상품별 10개 이벤트가 한 번으로 합쳐지고, batch 3,000에서는 대부분 상품별 30개가 한 번으로 합쳐져 처리량이 상승했다.

Uniform 분포는 20,000개 이벤트가 20,000개 상품에 한 번씩 배치되므로 batch를 키워도 합칠 대상이 없다. 결과도 모든 설정에서 이벤트당 Redis 호출 1회와 약 14.7~15.1K events/s로 유지됐다. 즉 이번 최적화의 효과는 batch size 자체가 아니라 **한 batch 안의 동일 ranking key/member 반복도**에 의해 결정된다. Hot과 Uniform의 절대 처리량은 cardinality도 다르므로 같은 분포 내부에서만 비교했다.

## 2. At-least-once 재시도 오차

| Batch | 실패 지점 | 점수 과대 계상 | 오차 상품 수 | Top 20 일치율 |
| ---: | ---: | ---: | ---: | ---: |
| 50 | 0% | 0.0000% | 0 | 100% |
| 50 | 25% | 0.0634% | 12 | 95% |
| 50 | 75% | 0.1807% | 37 | 80% |
| 500 | 0% | 0.0000% | 0 | 100% |
| 500 | 25% | 0.6130% | 125 | 35% |
| 500 | 75% | 1.9027% | 375 | 25% |
| 3,000 | 0% | 0.0000% | 0 | 100% |
| 3,000 | 25% | 3.7465% | 750 | 40% |
| 3,000 | 75% | 11.2491% | 2,250 | 100% |

0% 실패는 첫 쓰기 전에 실패하므로 전체 재처리 뒤 오차가 없다. 25%와 75%는 일부 aggregate write가 성공한 뒤 같은 batch 전체를 다시 처리해 그 성공분만 중복된다. 이 실험에서는 각 이벤트가 서로 다른 aggregate target을 만들었으므로 raw batch가 커질수록 부분 성공한 고유 write와 중복 상품 수도 함께 커졌다. 실제 오차 반경을 결정하는 값은 raw event 수 자체가 아니라 재시도 전에 성공한 고유 aggregate write 수다.

다만 batch 3,000의 75%처럼 많은 상품이 비슷한 방향으로 과대 계상되면 절대 점수는 크게 틀려도 Top 20 구성은 유지될 수 있다. 반대로 batch 500의 75%처럼 상위권 일부가 선택적으로 중복되면 더 작은 총점 오차로도 Top 20 구성이 크게 바뀔 수 있다. 여기서 일치율은 순서를 무시한 집합 overlap이므로 순위 순서 보존까지 증명하지 않는다. 운영 관측에서는 총점 오차 대신 재처리 횟수, 영향 member 수, Top-N overlap과 순서 변동을 함께 봐야 한다.

## 3. API cardinality 기준선

아래 값은 오름차순/내림차순 6회에서 `최소 / 중앙 / 최대`다.

| 경계 | Members | Page | RPS | p50 ms | p95 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Redis | 1천 | 20 | 3,750 / 9,613 / 14,632 | 0.070 / 0.086 / 0.192 | 0.087 / 0.225 / 0.508 |
| Redis | 1만 | 20 | 6,321 / 8,429 / 11,098 | 0.076 / 0.082 / 0.135 | 0.136 / 0.207 / 0.396 |
| Redis | 10만 | 20 | 6,699 / 9,622 / 14,720 | 0.064 / 0.096 / 0.138 | 0.094 / 0.156 / 0.226 |
| Redis | 1천 | 100 | 5,059 / 7,233 / 8,856 | 0.101 / 0.129 / 0.174 | 0.184 / 0.220 / 0.314 |
| Redis | 1만 | 100 | 6,740 / 7,711 / 8,425 | 0.104 / 0.116 / 0.132 | 0.163 / 0.191 / 0.211 |
| Redis | 10만 | 100 | 5,284 / 6,461 / 9,369 | 0.099 / 0.128 / 0.152 | 0.133 / 0.258 / 0.313 |
| Full HTTP | 1천 | 20 | 170 / 398 / 785 | 1.152 / 2.600 / 5.497 | 1.847 / 4.680 / 8.233 |
| Full HTTP | 1만 | 20 | 353 / 414 / 504 | 1.733 / 2.148 / 2.626 | 3.430 / 4.252 / 4.534 |
| Full HTTP | 10만 | 20 | 183 / 390 / 607 | 1.416 / 2.799 / 5.018 | 2.742 / 4.659 / 7.329 |
| Full HTTP | 1천 | 100 | 215 / 278 / 396 | 2.187 / 3.386 / 4.442 | 4.323 / 5.737 / 6.660 |
| Full HTTP | 1만 | 100 | 255 / 342 / 478 | 1.930 / 2.663 / 3.379 | 3.386 / 4.622 / 7.878 |
| Full HTTP | 10만 | 100 | 237 / 344 / 450 | 1.941 / 2.603 / 3.937 | 3.358 / 4.769 / 7.186 |

실행 순서를 뒤집자 같은 cardinality의 full HTTP 결과가 크게 달라졌다. 예를 들어 1천/Top 20은 전체 6회에서 170~785 RPS로 넓게 퍼졌다. 이는 JIT, 애플리케이션 캐시, DB buffer, 커넥션 준비 등 순서 효과가 cardinality 효과보다 클 수 있음을 보여준다. 따라서 10만 member가 1천 member보다 빠르다고 결론 내릴 수 없다.

반대로 말하면 이 데이터에서는 1천에서 10만으로 커질 때 Top 20/100 성능이 단조롭게 악화되는 패턴도 관찰되지 않았다. Redis p95는 모든 조합과 run에서 1ms 미만이었고 full HTTP p95는 Redis보다 훨씬 컸다. 현 구조의 우선 병목 후보는 ZSET range 조회보다 HTTP 직렬화와 상품/브랜드 aggregation 경로다.

Redis `MEMORY USAGE`는 약 85.9KB/1천, 970KB/1만, 9.30MB/10만 member였다. 관측 평균은 약 86~97 bytes/member다. 2일 sliding TTL로 두 날짜가 공존하면 단순 합산 약 18.6MB이며, 실제 운영에서는 allocator, 다른 키, 복제본, fork 여유 메모리를 별도로 잡아야 한다.

## 4. API 동시성 부하

고정 조건은 ZSET 10만 member, Top 20, run당 1,000개 요청이다. 표는 3회 중앙값이며 괄호는 최소~최대다.

| Concurrency | Full HTTP RPS | Full HTTP p95 | 해석 |
| ---: | ---: | ---: | --- |
| 1 | 382.1 (239.3~532.4) | 3.937ms (3.267~6.187) | 순차 기준선 |
| 25 | 1,129.6 (1,054.4~1,680.3) | 40.436ms (24.899~40.826) | 처리량 증가, queueing 시작 |
| 100 | 1,297.2 (1,162.3~1,351.9) | 154.281ms (124.078~154.367) | 처리량 이득은 작고 지연 급증 |

concurrency 25에서 100으로 4배 늘려도 중앙 RPS는 약 14.8%만 증가했고 p95는 약 3.8배가 됐다. 이 장비와 same-JVM 구성에서는 더 많은 동시 요청이 유효 작업보다 대기 시간을 키우는 포화 신호다. 이 수치로 운영 인스턴스의 최대 RPS를 정하거나 production thread/connection pool을 튜닝해서는 안 된다.

## 설계 결정

1. **배치 내부 집계는 유지한다.** Hot workload에서 Redis 호출 감소와 처리량 개선이 충분히 확인됐다.
2. **`max.poll.records=3000`을 처리량만 보고 더 키우지 않는다.** Hot workload의 batch 3,000은 집계 후 고유 Redis write가 약 100개여서 처리량이 가장 높았다. 반면 재시도 오차는 raw batch 크기보다 부분 성공한 고유 aggregate write 수에 좌우됐다. 현재 값은 근사 랭킹이라는 경계 안에서 유지하되 이 값을 곧바로 오차량으로 해석하지 않고, 정확성이 요구되면 batch 축소만으로 해결하지 말고 Inbox/idempotency를 검토한다.
3. **Top-N 응답 캐시는 당장 추가하지 않는다.** concurrency 1 cardinality 기준선에서는 10만 member까지 Redis p95가 sub-ms였고 cardinality에 따른 단조 악화가 없었다. 먼저 운영 trace로 상품/브랜드 aggregation과 직렬화 시간을 분리한다.
4. **부하 보호는 HTTP 경로를 중심으로 본다.** 로컬에서 concurrency 100은 throughput 증가보다 tail latency 증가가 컸다. 운영에서는 인스턴스별 부하 테스트로 connection pool, thread pool, timeout과 허용 concurrency를 다시 정한다.
5. **랭킹 품질은 Top-N 기준으로 관측한다.** 재시도 시 전체 score overcount와 Top 20 변동이 일치하지 않았으므로, 복구 정책을 결정할 때 Top-N overlap과 재처리 영향 member 수를 함께 본다.

## 한계와 다음 실험

- Streamer 입력은 제출용 목표였던 10만 건이 아니라 시나리오당 2만 건이다. 처리량의 장시간 안정성과 GC 영향은 10만~100만 이벤트로 다시 확인해야 한다.
- Streamer Redis 이미지는 mutable한 `redis:latest`다. 회귀 기준선으로 고정하려면 API와 동일하게 명시 버전으로 pin해야 한다.
- Kafka E2E 처리량, consumer lag drain, broker fetch 설정의 영향은 측정하지 않았다. 실제 `catalog-events` 발행 시각부터 Redis 반영 시각까지 p50/p95/p99를 별도 측정해야 한다.
- 재시도는 결정론적 실패 지점 실험이다. Chaos 환경에서 Redis timeout/connection failure를 주입해 실제 Kafka redelivery와 ACK 동작을 검증해야 한다.
- API는 same-JVM이며 단일 장비에서 서버와 부하 생성기가 CPU를 공유했다. 별도 load generator와 운영 유사 DB/Redis 네트워크에서 재측정해야 한다.
- cardinality run 사이 순서 효과가 컸다. 다음 측정은 scenario 순서를 매 run 무작위화하거나 각 cardinality를 독립 JVM으로 실행하고 더 많은 warmup/표본을 사용한다.

## 배치 비교 확장

이 보고서의 Kafka processor 집계와 API cardinality 결론은 그대로 유지한다. 추가로 시간별 영속 메트릭을 일간 절대값 ZSET으로 발행하는 배치 방식을 구현하고 재실행 정확성·신선도·Redis 명령 수를 비교했다. 결과는 [이벤트 랭킹과 배치 스냅샷 비교](04-event-vs-batch-ranking.md)에 분리했으며, ingestion 비용이 timer 밖인 배치 결과를 이벤트 E2E 성능처럼 해석하지 않는다.

## 기존 원본 데이터

- [Streamer 배치 집계](benchmark-results/streamer-aggregation.csv)
- [Streamer 재시도 오차](benchmark-results/streamer-retry-drift.csv)
- [API cardinality 오름차순](benchmark-results/api-cardinality-ascending.csv)
- [API cardinality 내림차순](benchmark-results/api-cardinality-descending.csv)
- [API load concurrency 1](benchmark-results/api-load-c1.csv)
- [API load concurrency 25](benchmark-results/api-load-c25.csv)
- [API load concurrency 100](benchmark-results/api-load-c100.csv)
