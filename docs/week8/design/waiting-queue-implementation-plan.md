# 대기열 시스템 — 구현 계획

> 설계 문서 3종의 내용을 실제 구현 순서로 재배열한 체크리스트입니다. 각 단계에서 "왜 이 순서인가"만 짧게 남기고, 상세 근거는 해당 설계 문서를 참고합니다.
>
> - 아키텍처·API·코드 구조: [waiting-queue-architecture.md](waiting-queue-architecture.md)
> - 용량·수치 계산: [waiting-queue-capacity-planning.md](waiting-queue-capacity-planning.md)
> - 장애 대응·운영: [waiting-queue-runbook.md](waiting-queue-runbook.md)

## 핵심 원칙

1. **의존 방향대로 아래(Redis 연산)에서 위(Controller)로** 쌓는다.
2. 그 안에서도 **가장 새롭고 검증이 까다로운 것(Lua 원자성)을 앞당기고**, **가장 건드리면 위험한 것(기존 주문 API)을 뒤로 미룬다.**
3. 순수 운영/안정성 보강(Circuit Breaker, Rate Limit, 알림 규칙)은 핵심 기능이 다 돌아간 뒤 마지막에 붙인다 — 설계 문서에서도 이미 "실측 전까지는 기본값" 정책으로 정리해뒀다.

---

## 1단계 — Redis 연산 계층

가장 아래 계층. 이후 모든 컴포넌트가 이 두 Repository에 의존한다.

- [x] `domain/queue/WaitingQueueRepository` 인터페이스 (`enter`, `rank`, `size`)
- [x] `infrastructure/queue/WaitingQueueRepositoryImpl` (기본/Master 템플릿 연산별 분기)
- [x] `domain/queue/EntryTokenRepository` 인터페이스 (`find`, `delete`)
- [x] `infrastructure/queue/EntryTokenRepositoryImpl`
- [x] Testcontainers Redis로 `ZADD`/`ZRANK`/`ZCARD`/`GET`/`DEL` 동작 통합 테스트

→ 세부 인터페이스/키 네이밍/템플릿 라우팅은 [architecture.md: 도메인 모델 설계](waiting-queue-architecture.md#도메인-모델-설계), [Redis 키·연산 매핑](waiting-queue-architecture.md#redis-키연산-매핑) 참고.

---

## 2단계 — Lua 원자성 (리스크가 가장 큰 부분을 먼저 검증)

뒤로 미루면 다른 컴포넌트와 통합하는 단계에서 문제가 터져 디버깅 범위가 넓어진다. 독립적으로 먼저 검증한다.

- [x] `resources/scripts/admit-batch.lua` 작성
- [x] `domain/queue/QueueAdmissionRepository` 인터페이스 (`admitBatch`)
- [x] `infrastructure/queue/QueueAdmissionRepositoryImpl` (`DefaultRedisScript`)
- [x] 통합 테스트: N명 pop + 토큰 발급 원자성, "대기열에도 없고 토큰도 없는" 중간 상태가 관측되지 않는지 검증

→ 원자성이 필요한 이유는 [architecture.md: ZPOPMIN과 토큰 발급 사이의 원자성](waiting-queue-architecture.md#zpopmin과-토큰-발급-사이의-원자성--lua-스크립트) 참고.

---

## 3단계 — 도메인 서비스

- [x] `WaitingQueueService` — Repository mock 단위 테스트
- [x] `EntryTokenService` — Repository mock 단위 테스트

---

## 4단계 — 설정 공유

스케줄러와 `QueueFacade`가 둘 다 이 값을 참조하므로, 둘 중 뭘 먼저 만들든 이게 먼저 있어야 한다.

- [x] `QueueProperties`(`@ConfigurationProperties(prefix = "queue")`) 작성
- [x] `application.yml`에 `queue.scheduler-interval-ms`, `queue.throughput-per-second` 추가

→ 계산 근거는 [capacity-planning.md: 스케줄러 실행 주기 & 배치 크기(N) 산정](waiting-queue-capacity-planning.md#스케줄러-실행-주기--배치-크기n-산정) 참고.

---

## 5단계 — 스케줄러

- [x] 스케줄러 컴포넌트(`@Scheduled(fixedDelayString = "${queue.scheduler-interval-ms}")`) 작성, `QueueAdmissionRepository.admitBatch(queueProperties.batchSize(), ttl)` 호출
- [x] `spring.task.scheduling.pool.size` 최소 3~4로 조정 (기존 `OutboxRelay`/`PaymentReconciliationScheduler`와의 스레드풀 경합 예방)
- [x] 헬스체크 gauge 코드 추가(`queue.scheduler.last.execution.timestamp`, 매 tick 종료 시 `AtomicLong` 갱신 + `MeterRegistry.gauge(...)` 등록)

**gauge를 여기서 만드는 이유**: 알림 규칙·대시보드(8단계)와 달리, gauge 코드 자체는 비용이 거의 없고 5~7단계에서 스케줄러/큐 엔드포인트를 직접 디버깅할 때 바로 써먹을 수 있는 도구다. "지금 스케줄러가 100ms마다 제대로 도는가"를 로그 대신 Actuator로 바로 확인할 수 있다.

→ 스레드풀 경합 배경은 [runbook.md: 스케줄러 장애 감지](waiting-queue-runbook.md#스케줄러-장애-감지--스레드풀-경합-예방--헬스체크-지표) 참고.

---

## 6단계 — 신규 엔드포인트 (대기열 자체)

기존 주문 흐름을 전혀 건드리지 않고 독자적으로 완성 가능한 부분.

- [x] `QueueFacade`(`enter`, `getPosition`) + `QueueInfo` — 통합 테스트(Testcontainers)
- [x] `QueueV1Controller`, `QueueV1Dto`, `QueueV1ApiSpec` — E2E 테스트
- [x] `http/commerce-api/queue.http` 등으로 수동 검증: `enter` → `position` polling → 스케줄러가 토큰 발급하는 것까지 눈으로 확인

→ 엔드포인트 계약·DTO는 [architecture.md: 엔드포인트 계약](waiting-queue-architecture.md#엔드포인트-계약), [DTO 설계](waiting-queue-architecture.md#dto-설계) 참고.

---

## 7단계 — 기존 주문 API 통합

기존에 잘 동작하던 기능을 건드리는 건 항상 마지막으로 미룬다.

- [x] `ErrorType.SERVICE_UNAVAILABLE`(503) 추가
- [x] `ApiControllerAdvice`에 Redis 연결 예외(`DataAccessException` 계열) 전용 핸들러 추가
- [x] `OrderFacade`에 `EntryTokenService` 의존 추가 — 진입 전 `verify`, 성공 후 `consume`(`delete`)
- [x] `OrderV1Controller`에 `X-Loopers-EntryToken` 헤더 추가
- [x] E2E: `enter` → `position` polling → 토큰 발급 → `POST /orders` 전체 플로우, 토큰 없음/만료 시 403 검증

→ 에러 처리 근거는 [architecture.md: 엔드포인트 계약 3번](waiting-queue-architecture.md#3-post-apiv1orders--기존-api-수정), [runbook.md: 에러 응답](waiting-queue-runbook.md#에러-응답--신규-errortypeservice_unavailable503) 참고.

---

## 8단계 — 안정성 보강 (후순위)

핵심 기능이 다 돌아간 뒤에 붙인다. 설계 문서에서도 실측 데이터 없이는 정밀하게 튜닝할 수 없다고 이미 정리했다.

- [x] Resilience4j Circuit Breaker 도입 (Redis 호출부 감싸기)
- [x] 주문 API 자체 Rate Limit (문턱값 150 TPS)
- [x] Prometheus 알림 규칙("스케줄러 마지막 성공 실행으로부터 5~10초 경과") + Grafana 대시보드

→ 파라미터 산정 방법론은 [runbook.md: 장애 감지 및 복구](waiting-queue-runbook.md#장애-감지-및-복구) 참고.

---

## 다음 논의 항목 (TODO)

- [ ] 실측 데이터(정상 상태 에러율·실제 Redis 호출 TPS·failover 소요 시간) 확보 후 8단계 파라미터 재튜닝
- [ ] 실제 부하테스트로 Rate Limit 문턱값(150 TPS) 초과 시 거부 동작 검증
