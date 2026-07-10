# 대기열 주문 처리 용량 벤치마크

실제 기본값 비교 결과와 해석은 [`02-waiting-queue-benchmark-results.md`](./02-waiting-queue-benchmark-results.md)에 기록한다.

## 목적

`waitingQueueBenchmark`는 JUnit 기반의 비교 실험 도구다. Spring Boot를 `RANDOM_PORT`로 실행하고 다음 실제 경로를 통과한다.

1. 테스트 프로필의 MySQL·Redis Testcontainers를 기동한다.
2. 사용자를 하나의 사전 계산된 BCrypt 해시로 DB에 직접 적재한다. 사용자별 비밀번호 해싱 시간은 setup에 포함하지 않는다.
3. `WaitingQueueRepository`에 고유 사용자를 결정적인 score 순서로 등록한다.
4. 운영 `WaitingQueueAdmitScheduler`는 끄고, JUnit pacing harness가 시나리오별 `WaitingQueueProperties`와 실제 `WaitingQueueAdmitService`를 사용해 fixed-delay 간격으로 입장시킨다.
5. 발급된 `X-Entry-Token`과 인증 헤더로 `POST /api/v1/orders`를 동시 호출한다.
6. 인증 필터의 사용자 DB 조회·BCrypt 검증, 주문 트랜잭션, 상품 재고 잠금, 기존 outbox 저장까지 완료된 HTTP 응답을 측정한다.

모든 주문은 시나리오당 하나의 고재고 상품을 공유한다. 따라서 이 수치는 일반적인 다상품 트래픽이 아니라, 동일 상품 행에 경합이 집중되는 **hot-row contention workload**의 비교값이다.

## 실행 전제

- 저장소 루트에서 Gradle wrapper로 실행한다.
- 로컬 Docker daemon이 실행 중이어야 한다.
- 벤치마크 태그는 일반 `test` 태스크에서 제외되며, 전용 태스크에서만 실행된다.
- 테스트 프로필에서 대기열 scheduler, outbox relay, payment reconciliation은 비활성화된다. outbox relay만 멈추며 주문 트랜잭션의 outbox 저장은 그대로 수행한다.
- `management.server.port=0`이므로 management 서버도 충돌 없는 임의 포트를 사용한다.
- Redis 테스트 픽스처는 현재 고정 버전이 아닌 mutable `redis:latest` 이미지를 사용한다. 이미지가 바뀐 시점의 결과끼리는 직접 비교하지 않는 편이 안전하다.

## 운영 적용 범위와 P1 이연 위험

이 벤치마크는 운영 scheduler를 끄고 단일 JUnit pacing harness로 입장시킨다. 따라서 다음 운영 장애·다중 인스턴스 위험은 측정하지 않는다.

- `ZPOPMIN`과 사용자별 입장 토큰 `SET`은 별도 명령이다. 두 명령 사이에 프로세스가 종료되면 사용자가 대기열에서는 제거되고 토큰은 발급받지 못할 수 있다.
- 모든 API 인스턴스가 scheduler를 실행한다. `ZPOPMIN`이 동일 사용자의 중복 pop은 막지만, 전체 입장 속도는 API 인스턴스 수에 따라 증가한다.

현재 과제 경로는 P0인 FIFO 공정성과 입장 토큰 재사용 방지를 먼저 해결한다. 위 항목은 Round 8에서 의도적으로 이연한 P1이며, 이번 벤치마크로 해결 또는 검증을 완료한 것으로 보지 않는다. 대안과 향후 검증 기준은 [`02-waiting-queue-benchmark-results.md`](./02-waiting-queue-benchmark-results.md#의도적으로-이연한-p1-운영-리스크)에 기록한다.

## 실행 명령

### 기본 실험

```bash
./gradlew :apps:commerce-api:waitingQueueBenchmark --rerun-tasks
```

`waitingQueueBenchmark` 자체는 항상 실행되도록 구성되어 있지만, 의존 태스크까지 포함한 완전히 새로운 측정을 위해 `--rerun-tasks` 사용을 권장한다.

### 빠른 동작 확인

```bash
./gradlew :apps:commerce-api:waitingQueueBenchmark \
  -PqueueBenchmarkUsers=4 \
  -PqueueBenchmarkConcurrency=2 \
  -PqueueBenchmarkBatchSizes=2 \
  -PqueueBenchmarkAdmitDelaysMs=10 \
  -PqueueBenchmarkRuns=1 \
  -PqueueBenchmarkWarmupUsers=2 \
  -PqueueBenchmarkDbPoolSize=2 \
  -PqueueBenchmarkLabel=smoke \
  --rerun-tasks
```

### 전체 설정 예시

```bash
./gradlew :apps:commerce-api:waitingQueueBenchmark \
  -PqueueBenchmarkUsers=120 \
  -PqueueBenchmarkConcurrency=40 \
  -PqueueBenchmarkBatchSizes=5,10,18,30 \
  -PqueueBenchmarkAdmitDelaysMs=50,100,250 \
  -PqueueBenchmarkRuns=3 \
  -PqueueBenchmarkWarmupUsers=20 \
  -PqueueBenchmarkDbPoolSize=20 \
  -PqueueBenchmarkOutputDir=/tmp/waiting-queue-capacity \
  -PqueueBenchmarkLabel=pool-20 \
  --rerun-tasks
```

### 일반 테스트

순수 설정·백분위·리포트 테스트는 `benchmark` 태그가 없으므로 일반 테스트에서 실행된다. 실제 용량 벤치마크는 실행되지 않는다.

```bash
./gradlew :apps:commerce-api:test
```

순수 도구 테스트만 실행하려면 다음 명령을 사용한다.

```bash
./gradlew :apps:commerce-api:test \
  --tests 'com.loopers.benchmark.queue.WaitingQueueBenchmarkConfigTest' \
  --tests 'com.loopers.benchmark.queue.WaitingQueueBenchmarkStatisticsTest' \
  --tests 'com.loopers.benchmark.queue.WaitingQueueBenchmarkReportTest'
```

## 파라미터

| Gradle 속성 | 기본값 | 의미 |
| --- | ---: | --- |
| `queueBenchmarkUsers` | `60` | 측정 시나리오별 고유 사용자 및 HTTP 주문 요청 수 |
| `queueBenchmarkConcurrency` | `20` | HTTP 요청 executor의 최대 동시 작업 수 |
| `queueBenchmarkBatchSizes` | `5,10,18` | 비교할 입장 배치 크기 목록 |
| `queueBenchmarkAdmitDelaysMs` | `100` | 배치 완료 후 다음 배치까지 기다리는 fixed delay(ms) 목록. `0` 허용 |
| `queueBenchmarkRuns` | `2` | batch × delay 조합별 반복 횟수 |
| `queueBenchmarkWarmupUsers` | `10` | 최초 batch/delay 조합으로 한 번 실행할 warmup 사용자 수. `0`이면 생략 |
| `queueBenchmarkDbPoolSize` | `10` | `datasource.mysql-jpa.main.maximum-pool-size`에 바인딩할 Hikari 최대 크기 |
| `queueBenchmarkOutputDir` | `apps/commerce-api/build/reports/waiting-queue` | CSV와 Markdown 출력 디렉터리 |
| `queueBenchmarkLabel` | `local` | 파일명과 보고서에 남길 실험 라벨 |

수치와 목록은 실행 전에 검증한다. 사용자·동시성·배치·반복·DB pool은 양수여야 하고, delay와 warmup은 0 이상이어야 한다. 목록의 빈 값과 중복도 거부한다. Spring 기동 후에는 요청한 DB pool max가 실제 Hikari max로 바인딩됐는지 다시 확인한다.

## 실험 순서

warmup은 보고서에서 완전히 제외한다. 이후 각 `batch size × admit delay × run`은 다음 순서로 독립 실행된다.

1. 이전 시나리오의 모든 Future와 executor 종료를 bounded wait로 확인한다.
2. MySQL 전체 테이블과 Redis를 정리한다.
3. 시나리오 고유 사용자, 브랜드, 하나의 고재고 공유 상품을 적재한다.
4. Hikari active/pending sampler를 시작한다.
5. 사용자를 실제 Redis waiting queue에 순차 score로 등록한다.
6. 첫 배치를 즉시 입장시키고, 이후 배치는 직전 `admit()` 완료 후 설정한 delay만큼 기다린다.
7. 각 입장 토큰을 조회한 즉시 RANDOM_PORT 주문 API 작업을 executor에 제출한다.
8. 모든 HTTP 작업을 bounded wait로 회수하고 executor와 sampler를 종료한다.
9. queue 잔여 수, token 잔여 수, DB 주문 수, outbox 수를 실제 저장소에서 조회한다.
10. 모든 조합이 끝나면 timestamped 보고서를 쓰고 마지막 상태를 정리한다.

HTTP 4xx/5xx, timeout, 연결 오류는 부하 결과로 기록하며 JUnit 실패로 처리하지 않는다. 잘못된 설정, 큐/토큰 순서가 깨진 harness 오류, bounded wait 초과, 시나리오 누락, 보고서 생성 실패만 테스트 실패가 된다.

## 측정값 정의

- `request latency`: 클라이언트 전송 직전부터 HTTP 응답 또는 오류까지다. 인증 DB 조회와 BCrypt 검증, 주문 트랜잭션, hot-row 상품 잠금, outbox 저장을 포함한다.
- `total duration`: Redis enqueue 시작부터 HTTP 작업 종료 후 잔여 상태 조회까지다.
- `e2e duration`: 모든 사용자의 enqueue 완료 후 첫 admit 직전부터 마지막 HTTP 작업 완료까지다.
- `queue drain duration`: 첫 admit 직전부터 Redis waiting queue의 마지막 사용자를 pop한 시점까지다.
- `attempted throughput TPS`: 완료된 전체 HTTP 요청 수 ÷ e2e duration이다. 빠른 실패 응답도 분자에 포함된다.
- `successful throughput TPS`: HTTP 2xx 요청 수 ÷ e2e duration이다. 실제 성공 처리 속도를 비교할 때 우선 사용한다.
- `theoretical admission TPS`: `batch size × 1000 ÷ fixed delay(ms)`다. delay가 `0`이면 `Infinity`로 기록한다.
- `observed admission TPS`: 실제 입장 사용자 수 ÷ queue drain duration이다. 첫 배치를 즉시 처리하므로 짧은 실험에서는 theoretical 값보다 높을 수 있다.
- `average request latency`: 모든 성공·실패 요청 지연시간의 산술 평균이다.
- `p50/p95/p99/max`: 모든 성공·실패 요청의 지연시간을 nearest-rank 방식으로 계산한다.
- `Hikari max active/pending`: Redis enqueue 직전부터 모든 HTTP 작업 및 executor 종료까지 5ms 간격으로 관찰한 최댓값이다.
- `effective Hikari max/min`: Spring 기동 후 Hikari가 실제 적용한 pool 설정이다.
- `remaining queue/tokens`: 종료 시 Redis에 남은 대기 사용자와 미소비 입장 토큰 수다.
- `persisted orders/outbox`: 종료 시 MySQL에 실제 커밋된 주문과 outbox 이벤트의 정확한 수다.

반복 실행을 합친 비교 표의 average와 p50/p95/p99/max는 run별 통계의 평균이 아니라, 같은 batch/delay 조합의 **raw request latency 전체를 합쳐서** 다시 계산한다. 집계 attempted TPS는 전체 요청 수를, successful TPS는 전체 HTTP 2xx 수를 run별 e2e duration 합으로 나눈 값이다. 보고서의 `Best successful throughput`도 successful TPS를 기준으로 선택한다.

## 출력 파일

기본 출력 위치는 다음과 같다.

```text
apps/commerce-api/build/reports/waiting-queue/
```

한 번의 실행은 동일한 `yyyyMMdd-HHmmss-SSS-label` prefix로 세 파일을 만든다.

```text
20260710-130231-789-local-scenarios.csv
20260710-130231-789-local-requests.csv
20260710-130231-789-local-report.md
```

- `*-scenarios.csv`: 조합/run별 duration, attempted/successful TPS, 평균·percentile, Redis·DB 최종 상태, Hikari 설정·관측값
- `*-requests.csv`: 사용자별 HTTP status, 성공 여부, latency, 실패 응답/예외
- `*-report.md`: 환경·설정, raw scenario 결과, pooled average/percentile 및 attempted/successful TPS 비교, 최고 successful throughput·최저 p95 관찰, 해석 caveat

## 권장 비교 절차

한 번에 여러 축을 바꾸면 병목 원인을 구분하기 어렵다. 다음 순서로 라벨과 나머지 조건을 고정해 비교한다.

1. 위의 빠른 동작 확인으로 Docker, 인증, queue/token, 주문/outbox, 보고서 생성을 검증한다.
2. 기본값으로 기준선을 2회 이상 측정한다.
3. users/concurrency/DB pool/delay를 고정하고 batch size만 비교한다.
4. 선택한 batch size에서 admit delay만 비교한다.
5. batch/delay를 고정하고 HTTP concurrency를 늘려 Hikari pending과 p95 변화를 본다.
6. 마지막으로 DB pool size를 바꾸며 max active/pending, persisted orders, outbox, 실패율을 함께 비교한다.

결론은 attempted TPS 하나가 아니라 `successful TPS`, `HTTP success`, `persisted orders`, `persisted outbox`, `remaining queue/tokens`, pooled average/p95, Hikari pending을 함께 보고 내린다. 로컬 same-JVM/Testcontainers 값은 환경 내 상대 비교용이며 운영 용량 보증값이 아니다.
