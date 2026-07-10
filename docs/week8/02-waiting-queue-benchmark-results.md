# Round 8 대기열 용량 실측 기록

## TL;DR

로컬 Testcontainers 환경에서 baseline 720건과 P0 보강 후 DB pool sweep·후보 반복 2,160건을 측정했고, 모든 요청과 주문·outbox 저장은 성공했다. `batch=18`, `delay=100ms`는 이론상 180명/s를 입장시켜도 pool을 10·20·40으로 늘린 결과가 일관되게 좋아지지 않았다. 현재 hot-row 주문 조건에서는 `batch=18`을 유지하기보다 `batch=5`와 `batch=10` 사이에서 지연시간과 처리량의 우선순위를 선택하는 편이 타당하다.

## 측정 목적

기존 `batch=18`, `delay=100ms` 설정은 Tomcat 최대 워커 200개에서 여유 10%를 제외한 값이었다. 이번 측정은 이 가정에 DB connection pool, 실제 주문 처리시간, 상품 row lock 경합을 반영하기 위해 진행했다.

측정 경로는 다음을 모두 포함한다.

1. Redis Sorted Set 대기열 등록
2. 실제 `WaitingQueueAdmitService`를 이용한 토큰 발급
3. 인증 DB 조회와 BCrypt 검증
4. `POST /api/v1/orders`
5. 상품 비관적 락, 주문 저장, outbox 저장
6. 주문 성공 후 입장 토큰 삭제

## 측정 환경

| 항목 | 값 |
| --- | --- |
| 실행 시각 | 2026-07-10 13:13~13:15 KST |
| Java | 21.0.11 |
| OS | macOS aarch64 |
| CPU logical processors | 10 |
| MySQL | Testcontainers `mysql:8.0` |
| Redis | Testcontainers `redis:latest` |
| Hikari max / min | 10 / 5 |
| 사용자 / HTTP concurrency | 60 / 20 |
| 반복 | 조합별 2회, 별도 warmup 10명 |
| 상품 조건 | 모든 주문이 하나의 고재고 상품을 공유하는 hot-row workload |

부하 생성기와 서버가 같은 JVM과 호스트를 사용하므로 절대적인 운영 용량이 아니라 동일 환경 안에서 설정값을 비교하는 자료로 해석한다.

## 실험 1 — Batch Size 비교

고정 조건은 `delay=100ms`, 사용자 60명, concurrency 20, Hikari max 10이다.

| Batch | 성공 | 성공 TPS | 평균 | p95 | Hikari max active / pending |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 5 | 120 / 120 | 44.098 | 123.861ms | 168.928ms | 10 / 0 |
| 10 | 120 / 120 | 52.611 | 319.861ms | 435.633ms | 10 / 10 |
| 18 | 120 / 120 | 49.662 | 360.410ms | 468.923ms | 10 / 10 |

### 관찰

- `batch=5`에서 `batch=10`으로 늘리면 성공 TPS는 약 19.3% 증가했지만 p95는 약 2.58배 증가했다.
- `batch=10`부터 Hikari pending이 10까지 발생했다. HTTP concurrency 20이 pool 10을 초과한 상태에서 한 번에 10명씩 입장시키며 connection 대기가 드러난 것으로 해석할 수 있다.
- `batch=18`은 이론상 admission rate가 180명/s지만 실제 성공 TPS는 `batch=10`보다 낮았다. 입장량을 늘려도 hot-row lock과 주문 처리 용량을 넘어서면 처리량은 증가하지 않고 지연시간만 악화될 수 있다.

## 실험 2 — Admit Delay 비교

고정 조건은 `batch=10`, 사용자 60명, concurrency 20, Hikari max 10이다.

| Delay | 성공 | 성공 TPS | 평균 | p95 | 관측 admission TPS | Hikari max pending |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 50ms | 120 / 120 | 45.696 | 377.787ms | 496.495ms | 120.430 | 10 |
| 100ms | 120 / 120 | 51.835 | 335.701ms | 472.896ms | 94.701 | 10 |
| 200ms | 120 / 120 | 43.248 | 260.610ms | 408.986ms | 53.730 | 10 |

### 관찰

- `delay=50ms`는 가장 빠르게 입장시켰지만 성공 TPS가 가장 높지 않았다. 주문 처리 속도보다 admission 속도가 빨라 대기 작업만 늘어난 것으로 볼 수 있다.
- `delay=100ms`가 세 조건 중 성공 TPS가 가장 높았다.
- `delay=200ms`는 평균과 p95를 낮췄지만 전체 처리량도 감소했다.
- delay를 늘려도 `batch=10`이 한 번에 만드는 burst 때문에 Hikari pending은 사라지지 않았다. 지연시간 안정화에는 delay뿐 아니라 batch 크기도 함께 봐야 한다.

## 실험 3 — P0 보강 후 DB Pool Sweep

P0 보강(Lua 기반 FIFO 순번 발급, 토큰 claim) 뒤 같은 workload에서 `delay=100ms`, 사용자 60명, HTTP concurrency 20, warmup 10명, 조합별 2회로 다시 측정했다. 각 pool에서 `batch=5`, `batch=10`을 먼저 비교하고, 기존 운영값인 `batch=18`을 별도 재검증했다.

| Hikari max | Batch | 성공 | 성공 TPS | 평균 | p95 | Hikari max active / pending |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 5 | 120 / 120 | 44.684 | 129.785ms | 182.497ms | 10 / 1 |
| 10 | 10 | 120 / 120 | 46.624 | 358.182ms | 484.821ms | 10 / 10 |
| 10 | 18 | 120 / 120 | 47.531 | 364.995ms | 444.728ms | 10 / 10 |
| 20 | 5 | 120 / 120 | 41.472 | 245.436ms | 461.620ms | 19 / 4 |
| 20 | 10 | 120 / 120 | 52.762 | 333.242ms | 451.108ms | 20 / 0 |
| 20 | 18 | 120 / 120 | 54.855 | 324.609ms | 481.255ms | 20 / 7 |
| 40 | 5 | 120 / 120 | 44.187 | 119.495ms | 162.132ms | 9 / 0 |
| 40 | 10 | 120 / 120 | 55.207 | 316.115ms | 417.668ms | 20 / 10 |
| 40 | 18 | 120 / 120 | 41.340 | 436.444ms | 610.331ms | 20 / 7 |

### 관찰

- pool 40의 `batch=10`은 이번 범위에서 성공 TPS 55.207, p95 417.668ms로 가장 높은 처리량과 `batch=18`보다 낮은 p95를 함께 보였다.
- `batch=18`은 pool 20에서 TPS가 54.855로 일시적으로 높았지만 p95가 `batch=10`보다 30.147ms 컸다. pool 40에서는 TPS가 41.340까지 떨어지고 p95가 610.331ms로 악화됐다.
- 따라서 batch를 18로 키우는 효과는 DB pool 크기에 대해 단조롭지 않다. 이 workload에서는 더 많은 동시 입장이 hot-row 경합과 요청 대기를 키울 수 있다는 기존 가설과 일치한다.
- pool 20의 `batch=5` 결과는 첫 반복의 connection 대기로 변동이 컸다. 조합별 2회 측정만으로 pool 크기 자체의 우열을 확정하지 않고, 다음 비교는 3회 이상 반복한다.

### 설정 판단

이번 결과만으로 production `batch=18`을 자동 변경하지 않는다. 다만 다음 후보로 좁힌다.

- **지연시간 안정성 우선:** `batch=5`, `delay=100ms` (pool 40 기준 p95 162.132ms, 성공 TPS 44.187)
- **처리량 우선:** `batch=10`, `delay=100ms` (pool 40 기준 p95 417.668ms, 성공 TPS 55.207)

`batch=18`은 세 pool에서 일관된 이점이 없고 pool 40에서 명확히 악화됐으므로, 다음 3회 반복 측정에서도 같은 경향이 나오면 `batch=10` 이하로 낮추는 변경을 별도 결정한다.

## 실험 4 — 후보 설정 3회 반복

실험 3의 후보인 `batch=5/10`, `delay=100ms`를 Hikari max `10/20/40`에서 각각 3회 반복했다. 사용자는 60명, HTTP concurrency는 20명, warmup은 10명으로 유지했다.

| Hikari max | Batch | 성공 | 성공 TPS | 평균 | p95 | Hikari max active / pending |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 5 | 180 / 180 | 44.599 | 133.914ms | 205.676ms | 10 / 0 |
| 10 | 10 | 180 / 180 | 55.200 | 309.532ms | 420.068ms | 10 / 10 |
| 20 | 5 | 180 / 180 | 33.257 | 215.592ms | 357.822ms | 15 / 3 |
| 20 | 10 | 180 / 180 | 49.834 | 353.511ms | 607.052ms | 20 / 4 |
| 40 | 5 | 180 / 180 | 45.059 | 135.694ms | 223.708ms | 12 / 1 |
| 40 | 10 | 180 / 180 | 54.710 | 321.603ms | 421.206ms | 20 / 5 |

### 관찰과 결정 보류

- `batch=10`은 pool 10과 40에서 약 54.7~55.2 TPS로 `batch=5`보다 약 21~24% 높은 처리량을 보였고, p95는 약 420ms였다.
- `batch=5`는 pool 10과 40에서 약 44.6~45.1 TPS, p95 약 206~224ms로 더 낮은 지연시간을 일관되게 보였다.
- pool 20의 세 번째 `batch=5` 실행은 전체 2,765.755ms, p95 1,396.281ms로 다른 반복과 크게 달랐다. 같은 시점의 `batch=10` 첫 반복도 p95 677.783ms로 높았다. 성공률은 100%였지만, 이 환경에서 pool 20이 pool 10·40보다 나쁘다는 결론으로 일반화하기에는 관측·반복 수가 부족하다.
- 따라서 **현재 기본값 `batch=18`은 유지하되 자동 변경하지 않는다.** 운영 목표가 처리량이면 `batch=10`, 지연시간 안정성이 우선이면 `batch=5`가 후보라는 근거는 확보했다. 실제 기본값 변경은 운영 SLA와 pool 20 이상치 원인 확인 후 별도로 결정한다.

## 현재 판단

이번 결과만으로 하나의 운영 설정을 확정하지 않는다. 현재 후보는 다음 두 가지다.

- **지연시간 안정성 우선:** `batch=5`, `delay=100ms`
  - Hikari pending 없이 p95 약 169ms
  - 성공 TPS는 약 44.1
- **처리량 우선:** `batch=10`, `delay=100ms`
  - 성공 TPS 약 52.2~52.6
  - Hikari pending 10, p95 약 436~473ms를 감수해야 함

`batch=18`, `delay=100ms`는 현재 workload와 DB pool 조건에서는 후보에서 제외하는 편이 합리적이다. 워커 스레드 수만으로 산정한 180명/s admission이 실제 주문 처리 용량과 맞지 않는다는 측정 결과가 있기 때문이다.

## 의도적으로 이연한 P1 운영 리스크

현재 과제 경로는 P0인 FIFO 공정성과 입장 토큰 재사용 방지를 먼저 해결한다. 아래 항목은 Round 8에서 해결·검증을 완료한 것으로 보지 않는 P1 운영 리스크다.

1. 현재 `ZPOPMIN`과 사용자별 입장 토큰 `SET`은 분리되어 있다. pop 이후 token 저장 전에 프로세스가 종료되면 사용자가 대기열에서 사라진 채 토큰을 받지 못할 수 있다.
2. 모든 API 인스턴스가 scheduler를 실행한다. `ZPOPMIN` 덕분에 같은 사용자를 중복 pop하지는 않지만, 인스턴스마다 batch를 처리하므로 전체 admission rate는 인스턴스 수에 따라 증가한다.

| 대상 | 대안 | 장점 | 트레이드오프 |
| --- | --- | --- | --- |
| pop-token 원자성 | **A. Lua로 pop과 token 발급을 원자 처리** | 가장 작은 범위에서 사용자 유실 구간을 제거한다. | 토큰을 script 안에서 생성할지 외부에서 전달할지 정해야 하고, 큰 batch는 script 실행 시간과 Redis 점유 시간을 늘린다. |
| pop-token 원자성 | **B. waiting ZSET에서 admitting/reservation ZSET으로 원자 이동 후 복구 worker 운영** | 발급 중인 사용자를 추적해 실패 후 token 재발급 또는 재대기시킬 수 있다. | 상태, timeout 정책, 복구 worker와 Redis 연산이 추가된다. |
| 다중 인스턴스 | **단일 scheduler 인스턴스 또는 leader election** | admission rate를 한 주체가 제어한다. | 전용 역할 운영 또는 leader 장애 감지·승계가 필요하다. |
| 다중 인스턴스 | **분산 락으로 scheduler 실행 직렬화** | API 배포 구조를 유지하면서 동시 실행을 막을 수 있다. | lock TTL·갱신·소유권 실패를 다뤄야 하며, 단순 mutex만으로는 호출 주기가 공유되지 않으므로 다음 실행 시각이나 permit도 함께 조정해야 한다. |

향후에는 다음 조건을 통과해야 P1을 해결한 것으로 판단한다.

- **pop/set 장애 주입:** pop 직후와 batch 중간의 token `SET` 전에 프로세스를 종료한다. 재시작 후 모든 pop 대상이 유효한 token을 갖거나 제한된 복구 시간 안에 reservation/대기열로 돌아와야 하며, 어느 상태에서도 조회되지 않는 사용자는 없어야 한다.
- **다중 인스턴스 입장률:** 동일한 batch/delay로 API 1대와 2대 이상을 실행한다. 전체 입장 수가 단일 scheduler 정책의 시간창별 허용량과 최초 1 batch burst를 넘지 않고, 사용자별 pop과 token 발급은 한 번이어야 한다.

## 다음 실험

1. pool 20에서 실행별 Hikari connection 생성 시간, GC pause, DB lock wait를 추가 수집해 이상치 원인을 구분한다.
2. Hikari pool별로 HTTP concurrency를 `10/20/40`으로 변경해 pending과 p95 변화를 본다.
3. shared-product와 unique-product workload를 분리해 DB pool 병목과 hot-row lock 병목을 구분한다.

## 재현 명령

Batch size 비교:

```bash
./gradlew :apps:commerce-api:waitingQueueBenchmark \
  -PqueueBenchmarkUsers=60 \
  -PqueueBenchmarkConcurrency=20 \
  -PqueueBenchmarkBatchSizes=5,10,18 \
  -PqueueBenchmarkAdmitDelaysMs=100 \
  -PqueueBenchmarkRuns=2 \
  -PqueueBenchmarkWarmupUsers=10 \
  -PqueueBenchmarkDbPoolSize=10 \
  -PqueueBenchmarkLabel=baseline \
  --rerun-tasks --no-daemon
```

Admit delay 비교:

```bash
./gradlew :apps:commerce-api:waitingQueueBenchmark \
  -PqueueBenchmarkUsers=60 \
  -PqueueBenchmarkConcurrency=20 \
  -PqueueBenchmarkBatchSizes=10 \
  -PqueueBenchmarkAdmitDelaysMs=50,100,200 \
  -PqueueBenchmarkRuns=2 \
  -PqueueBenchmarkWarmupUsers=10 \
  -PqueueBenchmarkDbPoolSize=10 \
  -PqueueBenchmarkLabel=delay-sweep \
  --no-daemon
```

원본 Markdown·scenario CSV·request CSV는 `apps/commerce-api/build/reports/waiting-queue/`에 생성된다.
