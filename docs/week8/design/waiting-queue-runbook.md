# 대기열 시스템 — 운영 Runbook

> 이 문서는 `waiting-queue-design.md`(원본, 통합본)를 단일 책임 원칙 관점에서 3개로 분리한 것 중 **"운영 Runbook"** 파트입니다. 장애 대응·모니터링·알림처럼 **온콜 상황에서 빠르게 훑어봐야 하는 내용**을 모읍니다. "왜 이렇게 만들었는가"보다 "지금 뭘 해야 하는가"에 최적화합니다.
>
> - 아키텍처·API·코드 구조: [waiting-queue-architecture.md](waiting-queue-architecture.md)
> - 용량·수치 계산: [waiting-queue-capacity-planning.md](waiting-queue-capacity-planning.md)

---

## 스케줄러 장애 감지 — 스레드풀 경합 예방 + 헬스체크 지표

**사전 예방이 먼저다**: 이 프로젝트에는 `spring.task.scheduling.pool.size` 설정이 없어, Spring Boot 기본값(1)이 그대로 적용된다. 이미 `@Scheduled`로 등록된 `OutboxRelay`(1초 주기)와 `PaymentReconciliationScheduler`(5초 주기)가 이 **단일 스레드**를 공유하는 중인데, 여기에 100ms 주기의 대기열 스케줄러까지 더하면 3개 작업이 스레드 1개를 나눠 쓰게 된다. `fixedDelay`는 이전 실행이 끝난 뒤부터 대기하는 방식이라, 스레드가 1개면 셋은 사실상 순차 실행된다 — 다른 스케줄러가 오래 걸리는 순간 대기열 스케줄러의 100ms 주기가 그대로 밀린다. **그래서 "스케줄러 장애"의 가장 흔한 원인은 코드 버그가 아니라 이 스레드풀 경합일 가능성이 높고, 감지 이전에 `spring.task.scheduling.pool.size`를 최소 3~4로 늘려 예방하는 게 먼저다.**

**헬스체크 지표**: 스케줄러가 마지막으로 성공 실행된 시각을 Micrometer Gauge로 노출한다(`queue.scheduler.last.execution.timestamp`). 매 tick 종료 시 `AtomicLong`을 갱신하고 `MeterRegistry.gauge(...)`로 등록해 Prometheus가 스크레이핑하도록 한다.

**알림 임계치**: 스케줄러 주기(100ms)를 그대로 임계치로 쓰지 않는다 — Prometheus 기본 스크레이핑 주기(보통 10~15초)보다 촘촘한 임계치는 오탐만 만든다. **"마지막 성공 실행으로부터 5~10초 경과"**를 알림 임계치로 잡는다.

**보조 지표**: `ZCARD`(Queue Depth)가 계속 증가하고 줄지 않는 것도 신호가 될 수 있지만, 이것만으로는 "스케줄러가 죽었다"와 "그냥 유입이 폭증했다"를 구분하지 못한다. 그래서 위 실행 시각 지표를 뒷받침하는 보조 지표로만 쓰고, 알림의 주된 판단 기준은 실행 시각 gap으로 둔다.

배치 크기(N=7)·처리량(TPS=70) 계산 근거는 [용량 산정 문서](waiting-queue-capacity-planning.md#스케줄러-실행-주기--배치-크기n-산정) 참고.

---

## 대기열 자체의 리스크

참고 자료가 제시한 4가지 리스크 중, 이 프로젝트에서 실제로 서버(백엔드)가 처리해야 하는 항목과 이미 앞선 설계 결정으로 해결된 항목을 구분한다.

| 리스크 | 서버 처리 필요 | 대응 |
|---|---|---|
| 토큰 미사용(자리만 차지) | 예 | TTL(`EX 300`, 5분)로 자동 만료 처리. "만료된 토큰 수만큼 다음 유저에게 추가 발급"하는 로직은 **불필요** — [Policy A](waiting-queue-capacity-planning.md#스케줄러-실행-주기--배치-크기n-산정)를 택해 활성 토큰 수와 무관하게 매 tick 고정 개수(N=7)를 발급하므로, 애초에 "활성 개수를 추적해 보충 발급"하는 구조 자체가 없다 |
| 어뷰징(중복 진입) | 예, 이미 해결됨 | `ZADD`의 member=userId 유일성으로 자료구조 차원에서 중복 방지([왜 Redis인가 #2](waiting-queue-architecture.md#2-동시-진입에-대한-순서-보장--중복-방지를-애플리케이션-락-없이)). "비로그인 상태의 디바이스 핑거프린팅" 대응은 이 프로젝트에서 불필요 — [설계 결정 1번](waiting-queue-architecture.md#1-유저-식별-방식)에서 이미 `X-Loopers-LoginId`/`X-Loopers-LoginPw` 기반 로그인을 전제로 했으므로 비로그인 진입 시나리오 자체가 없음 |
| 스케줄러 장애 | 예 | 위 "[스케줄러 장애 감지](#스케줄러-장애-감지--스레드풀-경합-예방--헬스체크-지표)" 참고 |
| 과도한 Polling 부하 | 아니오 | Redis `ZRANK`는 O(log N)이라 참고 자료 예시(초당 5,000건) 수준은 충분히 감당 가능. 순번 구간별 폴링 주기 동적 조정은 클라이언트가 이미 받은 `position` 값으로 스스로 판단하는 프론트엔드 로직이라 서버 응답/로직 변경이 필요 없다. 이 프로젝트에 프론트엔드 앱이 없어(`commerce-api`/`commerce-batch`/`commerce-streamer` 멀티모듈뿐) 스코프 밖이기도 하다 |

---

## Redis 장애 시 — Graceful Degradation

대기열의 핵심 인프라인 Redis 자체가 죽었을 때 어떻게 할지 결정한다.

### 결정: 전면 차단(신규 진입·기존 주문 완료 모두 차단) + 빠른 복구

**후보 3가지의 기각 근거:**

| 대안 | 기각 근거 |
|---|---|
| 대기열 우회(bypass) — 대기열 없이 `/orders` 직접 허용 | 대기열이 없으면 트래픽이 [주문 API 자체 Rate Limit](waiting-queue-architecture.md#thundering-herd-완화-전략--3가지-후보-중-선택)(문턱값 150 TPS)에 그대로 부딪혀 초과분이 거부된다. "대기 중" 화면과 "주문 거부"는 체감이 완전히 다르다 — 전자는 유저가 참고 기다리지만 후자는 "구매 실패"로 느껴져 CS 문의가 폭증한다 |
| Fallback 큐 — 로컬 메모리 | OOM 위험도 있지만 더 근본적으로, `commerce-api`가 여러 인스턴스로 뜨면 인스턴스마다 별도의 로컬 큐를 가지게 되어 **"전역적으로 하나의 공정한 순서"라는 대기열의 존재 목적 자체가 깨진다**. 이건 애초에 Redis(공유 상태)를 쓴 이유와 정면으로 배치된다 |
| Fallback 큐 — Kafka | Kafka는 파티션 내 순서 자체는 잘 보장하지만, "내가 지금 몇 번째인지"를 즉시 answer하는 순위 조회(`ZRANK` 상당) 기능이 없다. Fallback으로 전환하면 `GET /queue/position`의 실시간 순번 기능이 죽는다 — [왜 Redis인가 #1](waiting-queue-architecture.md#1-몇-번째인지--상대적-순위-쿼리-그것도-초당-수천-번)에서 Kafka 대신 Redis를 고른 이유와 동일한 논리 |
| Fallback 큐 — DB | 순환 논리다. 애초에 이 대기열을 만든 이유가 DB 부하를 막기 위해서인데, 장애 시 그 DB를 큐 저장소로 다시 쓰는 건 자기모순이다([왜 Redis인가 #4](waiting-queue-architecture.md#4-보호-대상과-대기열-상태-저장소의-분리)와 동일한 논리) |

### 이미 토큰을 발급받은 유저의 처리

**"전면 차단"은 신규 진입(`/queue/enter`)뿐 아니라, 이미 토큰을 발급받고 `/orders` 완료만 남은 유저도 예외 없이 막는다.**

이건 별도로 구현해야 하는 결정 사항이 아니라 **`EntryTokenRepository`/`EntryTokenService`가 Redis 연결 예외를 삼키지만 않으면 자동으로 일어나는 기본 동작**이다. `OrderFacade`의 토큰 검증(`entryTokenService.verify()`)도 결국 Redis 조회이므로, Redis가 죽으면 이 호출이 예외(`RedisConnectionFailureException` 등, Spring Data Redis의 `DataAccessException` 계열)를 던지고 그대로 전파되어야 한다. 이 예외를 여기서 잡아 "토큰 없음"(`FORBIDDEN`)으로 둔갑시키면 정상 상황(토큰이 진짜 없음)과 Redis 장애가 똑같이 403으로 뭉개져 버린다(자세한 내용은 [설계 문서: 엔드포인트 계약 3번](waiting-queue-architecture.md#3-post-apiv1orders--기존-api-수정) 참고).

더 근본적으로, 토큰이 **불투명한 문자열로 Redis에만 존재하고 "키가 있는지 없는지"로만 검증**하는 구조라서, Redis가 죽으면 검증할 데이터 소스 자체가 없다 — fail-open(이미 토큰 가진 유저만 봐주기) 로직을 짜고 싶어도 참조할 게 없어 불가능하다. 이를 가능하게 하려면 서명된(self-contained) 토큰(JWT/HMAC로 `userId`+만료시각을 담아 Redis 조회 없이 로컬에서 서명·만료만 검증)으로 토큰 설계 자체를 바꿔야 하는데, 이건 이번 스코프를 넘어서는 큰 변경이라 **"설계상 받아들인 한계"로 명시하고 넘어간다.** 필요성이 커지면 별도로 재검토한다.

### 에러 응답 — 신규 `ErrorType.SERVICE_UNAVAILABLE`(503)

Redis 장애로 인한 실패는 정상적인 비즈니스 검증 실패(`FORBIDDEN` 등)와 의미가 다르므로 별도 카테고리가 필요하다. 기존 5종(`INTERNAL_ERROR`/`BAD_REQUEST`/`NOT_FOUND`/`CONFLICT`/`FORBIDDEN`) 중 "일시적으로 서비스를 이용할 수 없으니 잠시 후 다시 시도하라"에 해당하는 게 없어, 이번엔 새로 추가한다.

```java
SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(), "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.");
```

`ApiControllerAdvice`에 Redis 연결 예외(`DataAccessException` 계열, Circuit Breaker 도입 후에는 `CallNotPermittedException`도 포함)를 잡아 이 `ErrorType`으로 변환하는 전용 핸들러를 추가한다. Spring은 등록된 핸들러 중 가장 구체적인 예외 타입을 우선 매칭하므로, 기존 캐치올(`handle(Throwable e)` → `INTERNAL_ERROR`)보다 이 핸들러가 항상 먼저 적용된다. `/queue/enter`, `/queue/position`, `/orders` 모두 Redis에 의존하므로 이 핸들러 하나로 세 엔드포인트의 Redis 장애가 공통으로 커버된다.

### 장애 감지 및 복구

Spring Boot Actuator의 Redis `HealthIndicator`를 기반으로, Resilience4j Circuit Breaker로 Redis 호출부를 감싸는 방향을 검토한다 — Redis 응답 없음을 빠르게 감지해 즉시 차단 응답을 주고, 복구가 감지되면 자동으로 정상 흐름을 재개한다.

**Circuit Breaker 파라미터(`failureRateThreshold`, `slidingWindowSize`, `waitDurationInOpenState` 등)를 정하는 근거**: 이런 값들은 표준값을 그대로 가져다 쓰는 게 아니라 보통 세 가지 실측 데이터에서 역산한다.

1. **실패율 임계치(`failureRateThreshold`)·최소 호출 수(`minimumNumberOfCalls`)** — 평소 정상 상태의 베이스라인 에러율에서 나온다. "정상적인 노이즈"와 "진짜 장애"를 가르는 선이라, 실제 운영 중 관찰된 에러율 분포가 있어야 정확히 잡을 수 있다.
2. **슬라이딩 윈도우 크기(`slidingWindowSize`)** — 실제 호출 빈도(TPS)에서 나온다. 호출량 대비 윈도우가 너무 크면 반응이 느리고(진짜 장애도 한참 지나야 감지), 너무 작으면 우연한 2~3번 실패만으로 오탐이 난다.
3. **Open 상태 유지 시간(`waitDurationInOpenState`)** — 의존 대상(Redis)이 실제로 복구되는 데 걸리는 시간(예: Sentinel/Cluster failover 소요 시간)에서 나온다. 임의로 정하면 "아직 복구 안 됐는데 너무 자주 찔러보거나" "이미 복구됐는데 한참 뒤에야 재개"하는 문제가 생긴다.

**오탐 vs 미탐 트레이드오프**: 셋 다 공통으로, 오탐(너무 예민해서 멀쩡한데 차단)과 미탐(너무 둔감해서 진짜 장애를 못 잡음) 중 뭘 더 감수할지 정해야 하는데, [p95 대신 p99를 쓴 이유](waiting-queue-capacity-planning.md#스케줄러-실행-주기--배치-크기n-산정)와 같은 논리로 **미탐 쪽 비용(장애를 못 잡아 DB까지 부하가 번짐)이 오탐 쪽 비용(멀쩡한데 살짝 일찍 차단)보다 훨씬 크므로, 더 예민한(sensitive) 쪽으로 기울인다.**

**결정**: 위 세 가지 근거를 뒷받침할 실측 데이터(정상 상태 에러율, 실제 Redis 호출 TPS, 실제 failover 소요 시간)가 이 프로젝트엔 아직 없다. 그래서 **실측 전까지는 Resilience4j 기본값을 채택**하고, 운영 중 관찰되는 실제 에러율·호출량으로 추후 튜닝한다.

---

## 다음 논의 항목 (TODO)

- [ ] 실측 데이터(정상 상태 에러율·실제 Redis 호출 TPS·failover 소요 시간) 확보 후 Circuit Breaker 파라미터 재튜닝 — [용량 산정 문서](waiting-queue-capacity-planning.md)의 TPS 실측치와 함께 갱신
