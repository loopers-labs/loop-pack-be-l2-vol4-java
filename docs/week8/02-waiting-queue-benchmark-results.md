# Round 8 대기열 용량 실측 기록

## TL;DR

로컬 Testcontainers 환경에서 720건의 주문을 측정한 결과, 모든 요청과 주문·outbox 저장은 성공했다. 다만 `batch=18`, `delay=100ms`는 이론상 180명/s를 입장시켜도 실제 성공 처리량이 약 49.7 TPS에 머물렀고 Hikari pending이 10까지 증가했다. 현재 hot-row 주문 조건에서는 입장량을 더 늘리는 것보다 `batch=5`와 `batch=10` 사이에서 지연시간과 처리량의 우선순위를 선택하는 편이 타당하다.

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

## 현재 판단

이번 결과만으로 하나의 운영 설정을 확정하지 않는다. 현재 후보는 다음 두 가지다.

- **지연시간 안정성 우선:** `batch=5`, `delay=100ms`
  - Hikari pending 없이 p95 약 169ms
  - 성공 TPS는 약 44.1
- **처리량 우선:** `batch=10`, `delay=100ms`
  - 성공 TPS 약 52.2~52.6
  - Hikari pending 10, p95 약 436~473ms를 감수해야 함

`batch=18`, `delay=100ms`는 현재 workload와 DB pool 조건에서는 후보에서 제외하는 편이 합리적이다. 워커 스레드 수만으로 산정한 180명/s admission이 실제 주문 처리 용량과 맞지 않는다는 측정 결과가 있기 때문이다.

## 다음 실험

1. `batch=5/10`, `delay=100ms`를 고정하고 Hikari max를 `10/20/40`으로 변경한다.
2. Hikari pool별로 HTTP concurrency를 `10/20/40`으로 변경해 pending과 p95 변화를 본다.
3. shared-product와 unique-product workload를 분리해 DB pool 병목과 hot-row lock 병목을 구분한다.
4. 각 실험을 3회 이상 반복하고 pooled p95와 성공 TPS를 비교한다.

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
