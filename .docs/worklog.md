# Worklog Snapshot

이 문서는 현재 작업 상태를 이어가기 위한 최신 스냅샷이다. 누적 로그가 아니라 현재 기준만 유지한다.

## 현재 상태

| 항목 | 내용 |
| --- | --- |
| 날짜 | 2026-07-05 |
| 브랜치 | `volume-8` |
| 현재 단계 | 8주차 대기열 구현 및 commerce-api 전체 회귀 검증 완료 |
| 작업 범위 | 질문을 1개씩 확정하며 8주차 Redis 기반 주문 대기열 구현 진행 |
| 구현 범위 | Redis adapter, 대기열 진입/순번 조회 API, 입장 token/worker/scheduler, 주문 API queue token 검증과 성공 후 token 삭제 |
| 제외 범위 | Redis 장애 fallback 정책 확정, SSE |
| 기준 문서 | `AGENTS.md`, `.docs/README.md`, `.docs/design-review.md`, `.docs/worklog.md`, `.docs/domain.md`, `.docs/architecture.md`, `.codeguide/loopers-8-week.md` |
| 제출 문서 | `.docs/design/*` 4개 파일은 volume-2 설계 이력으로 보존하며 이번 작업에서 수정하지 않음 |

## 최근 결정

- 8주차 과제 가이드는 주문 API 앞단의 Redis 기반 대기열, 입장 토큰, 스케줄러, polling 순번 조회를 Must-Have로 정리한다.
- SSE, polling 주기 동적 조절, Thundering Herd 완화, Redis 장애 fallback은 Nice-To-Have로 분리한다.
- 주문 생성 이후 이벤트 발행, Kafka 파이프라인, Metrics 집계는 Round 7에서 구축한 구조를 재사용하는 전제로 둔다.
- 8주차 구현은 사용자의 요청에 따라 질문을 한 번에 1개씩 확인한 뒤 진행한다.
- 대기열은 주문 API 앞단의 관문이므로 별도 최상위 `queue` 모듈을 만들지 않고 `ordering.queue` 하위 경계에 둔다.
- 대기열 범위는 주문 API 전체를 보호하는 전역 대기열 1개로 시작한다.
- Redis key는 대기열 `commerce:ordering:queue:v1:waiting`, 입장 토큰 `commerce:ordering:queue:v1:token:{userId}`를 기준으로 한다.
- Redis Sorted Set score는 `INCR commerce:ordering:queue:v1:sequence` 결과를 사용해 동시 진입에서도 단조 증가 순서를 만든다. userId는 member로 저장하고 `ZADD NX`로 중복 진입을 막는다.
- 입장 토큰 TTL은 5분이다. 토큰 값은 UUID 문자열로 발급하고 주문 API의 `X-Loopers-Queue-Token` 헤더와 Redis 값을 비교한다.
- 입장 스케줄러 기본값은 `commerce.workers.order-queue.enabled=true`, `initial-delay-ms=1000`, `fixed-delay-ms=1000`, `admit-batch-size=10`으로 시작한다.
- 대기열 API는 `POST /api/v1/queue/enter`, `GET /api/v1/queue/position`을 사용한다. 사용자 식별은 기존 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더를 따른다.
- 대기열 진입/조회 API는 같은 응답 DTO를 사용한다. 필드는 `status`, `position`, `waitingCount`, `estimatedWaitSeconds`, `recommendedPollingIntervalSeconds`, `token`이고 상태값은 `WAITING`, `READY`, `NOT_QUEUED`이다.
- 주문 API에 `X-Loopers-Queue-Token` 헤더가 없거나 Redis 토큰과 일치하지 않으면 `ORDER_QUEUE_TOKEN_REQUIRED` 403으로 거부한다.
- 구현 이름은 `OrderQueueController`, `OrderQueueDto`, `OrderQueueService`, `OrderQueueAdmissionWorker`, `OrderQueueAdmissionWorkerScheduler`, `OrderQueueRepository`, `RedisOrderQueueRepository`, `OrderQueueStatus`를 사용한다.
- 첫 TDD 범위는 `RedisOrderQueueRepository` adapter 테스트로 시작했다.
- `RedisOrderQueueRepository`는 `INCR commerce:ordering:queue:v1:sequence`와 `ZADD NX`로 진입 순서와 중복 방지를 처리한다.
- 입장 토큰 repository 범위는 `issueToken`, `findToken`, `isValidToken`, `deleteToken`, `admitNext`까지 포함한다.
- `admitNext`는 Redis `ZPOPMIN`으로 대기열 앞에서 N명을 제거한 뒤 각 사용자에게 UUID 토큰을 TTL과 함께 발급한다.
- `OrderQueueService`는 입장 토큰이 있으면 `READY`를 우선 반환한다. 토큰이 없고 대기열에 있으면 `WAITING`, 둘 다 없으면 `NOT_QUEUED`를 반환한다.
- 예상 대기 시간은 `ceil(position / admitBatchSize) * schedulerIntervalSeconds`로 계산한다. `READY`는 0초, `NOT_QUEUED`는 null이다.
- 권장 polling 간격은 예상 대기 시간을 기준으로 1~10초 사이를 반환한다. `READY`는 0초, `NOT_QUEUED`는 null이다.
- `OrderQueueService`는 기본 설정으로 토큰 TTL 5분, 배치 크기 10, 스케줄러 주기 1초를 사용한다.
- `OrderQueueController`는 `POST /api/v1/queue/enter`, `GET /api/v1/queue/position`을 제공하고 기존 로그인 헤더를 검증한다.
- 누락 요청 헤더는 `MissingRequestHeaderException`을 `BAD_REQUEST`로 변환하도록 공통 예외 핸들러를 보강했다.
- `OrderQueueAdmissionWorker`는 기본 토큰 TTL 5분, 배치 크기 10으로 `OrderQueueRepository.admitNext`를 호출한다.
- `OrderQueueAdmissionWorkerScheduler`는 `commerce.workers.order-queue.enabled=true`일 때 활성화되고 1초 주기로 worker를 호출한다.
- `OrderQueueService`와 `OrderQueueAdmissionWorker`는 `commerce.workers.order-queue.token-ttl`, `admit-batch-size`, `fixed-delay-ms` 설정값을 사용한다.
- 주문 API는 `X-Loopers-Queue-Token`을 검증한다. 토큰이 없거나 Redis 저장값과 다르면 `ORDER_QUEUE_TOKEN_REQUIRED` 403으로 거부한다.
- 주문 생성 성공 후 사용한 입장 토큰을 삭제한다. 주문 생성 실패 시 토큰 삭제는 수행하지 않는다.
- E2E 테스트에서는 자동 scheduler로 인한 비결정성을 피하기 위해 `commerce.workers.order-queue.enabled=false`를 설정하고 worker는 수동 호출로 검증한다.
- `PaymentApiE2ETest`의 주문 생성 헬퍼도 queue token을 발급해 사용하도록 보강했다.
- 대기 인원이 입장 배치 크기를 초과하면 초과 사용자가 `WAITING`으로 남는 E2E 테스트를 추가해 처리량 제한 검증을 보강했다.
- `.http/order-queue.http`에 대기열 진입, 순번 조회, 입장 토큰 주문 요청 예시를 추가했다.
- `.codeguide/loopers-8-week.md` 체크리스트를 현재 구현 상태에 맞게 갱신했다. polling 부하 고려는 `recommendedPollingIntervalSeconds` 응답 필드로 반영했다.
- `commerce-api` 전체 테스트까지 통과해 8주차 필수 범위 회귀 검증을 완료했다.
- 남은 8주차 보강 후보는 SSE, Thundering Herd 완화, Redis 장애 fallback이며 모두 정책 결정 후 진행한다.

## 수정 파일 요약

| 구분 | 파일 |
| --- | --- |
| 8주차 가이드 | `.codeguide/loopers-8-week.md` |
| HTTP 예시 | `.http/order-queue.http` |
| 기준 문서 | `.docs/domain.md`, `.docs/architecture.md` |
| 대기열 service/worker | `apps/commerce-api/src/main/java/com/loopers/application/ordering/queue/OrderQueueService.java`, `apps/commerce-api/src/main/java/com/loopers/application/ordering/queue/OrderQueueResult.java`, `apps/commerce-api/src/main/java/com/loopers/application/ordering/queue/OrderQueueAdmissionWorker.java`, `apps/commerce-api/src/main/java/com/loopers/domain/ordering/queue/OrderQueueStatus.java` |
| 대기열 API | `apps/commerce-api/src/main/java/com/loopers/interfaces/api/ordering/queue/OrderQueueController.java`, `apps/commerce-api/src/main/java/com/loopers/interfaces/api/ordering/queue/OrderQueueDto.java` |
| 대기열 scheduler | `apps/commerce-api/src/main/java/com/loopers/infrastructure/ordering/queue/OrderQueueAdmissionWorkerScheduler.java`, `apps/commerce-api/src/main/resources/application.yml` |
| 대기열 Redis adapter | `apps/commerce-api/src/main/java/com/loopers/domain/ordering/queue/OrderQueueRepository.java`, `apps/commerce-api/src/main/java/com/loopers/infrastructure/ordering/queue/RedisOrderQueueRepository.java` |
| 주문 API 연계 | `apps/commerce-api/src/main/java/com/loopers/interfaces/api/ordering/OrderController.java`, `apps/commerce-api/src/main/java/com/loopers/interfaces/api/support/HeaderValidator.java`, `apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java` |
| 공통 API 예외 | `apps/commerce-api/src/main/java/com/loopers/interfaces/api/ApiControllerAdvice.java` |
| 테스트 | `apps/commerce-api/src/test/java/com/loopers/infrastructure/ordering/queue/RedisOrderQueueRepositoryTest.java`, `apps/commerce-api/src/test/java/com/loopers/application/ordering/queue/OrderQueueServiceTest.java`, `apps/commerce-api/src/test/java/com/loopers/application/ordering/queue/OrderQueueAdmissionWorkerTest.java`, `apps/commerce-api/src/test/java/com/loopers/infrastructure/ordering/queue/OrderQueueAdmissionWorkerSchedulerTest.java`, `apps/commerce-api/src/test/java/com/loopers/interfaces/api/ordering/queue/OrderQueueApiE2ETest.java`, `apps/commerce-api/src/test/java/com/loopers/interfaces/api/ordering/OrderApiE2ETest.java`, `apps/commerce-api/src/test/java/com/loopers/interfaces/api/payment/PaymentApiE2ETest.java` |
| 작업 스냅샷 | `.docs/worklog.md` |
| 제출 문서 | `.docs/design/*` 수정 없음 |

## 검증 결과

| 명령 | 결과 | 메모 |
| --- | --- | --- |
| `.\gradlew.bat --rerun-tasks :apps:commerce-api:test --tests com.loopers.application.ordering.queue.OrderQueueServiceTest` | 성공 | 권장 polling 간격 계산 단위 테스트 통과 |
| `LOOPERS_TESTCONTAINERS_ENABLED=false .\gradlew.bat --rerun-tasks :apps:commerce-api:test --tests com.loopers.application.ordering.queue.OrderQueueServiceTest --tests com.loopers.application.ordering.queue.OrderQueueAdmissionWorkerTest --tests com.loopers.infrastructure.ordering.queue.OrderQueueAdmissionWorkerSchedulerTest --tests com.loopers.infrastructure.ordering.queue.RedisOrderQueueRepositoryTest --tests com.loopers.interfaces.api.ordering.queue.OrderQueueApiE2ETest --tests com.loopers.interfaces.api.ordering.OrderApiE2ETest --tests com.loopers.interfaces.api.payment.PaymentApiE2ETest` | 성공 | 권장 polling 간격 응답 포함, 8주차 대기열/주문/결제 focused 회귀 통과 |
| `LOOPERS_TESTCONTAINERS_ENABLED=false .\gradlew.bat --rerun-tasks :apps:commerce-api:test` | 성공 | MySQL/Redis/Kafka compose 기동 후 commerce-api 전체 회귀 통과 |

## 환경 메모

- 현재 브랜치는 `volume-8`이다.
- `.codeguide/loopers-8-week.md`는 작업 시작 시점에 Git 미추적 파일이었다.
- 작업 시작 시점에 `tmp/`도 Git 미추적 항목으로 존재했으며 이번 작업에서는 건드리지 않았다.
- focused Redis test 전 `docker compose -f docker\infra-compose.yml up -d redis-master redis-readonly`로 로컬 Redis를 기동했다.
- 전체 commerce-api test 전 `docker compose -f docker\infra-compose.yml up -d mysql redis-master redis-readonly kafka`로 로컬 MySQL/Redis/Kafka를 기동했다.
- Gradle 실행 시 현재 셸에 `JAVA_HOME`이 없으면 `C:\Users\woodo\.jdks\ms-21.0.9`를 사용한다.
- Docker 기반 MySQL Testcontainers는 현재 환경에서 기동 실패해, Redis adapter 테스트는 `LOOPERS_TESTCONTAINERS_ENABLED=false`와 Redis 전용 Spring context로 검증했다.

## 다음 작업

1. 제출 전 Git diff를 기준으로 포함/제외 파일을 최종 확인한다.
2. SSE, Thundering Herd 완화, Redis 장애 fallback 중 추가 보강 여부를 선택한다.
