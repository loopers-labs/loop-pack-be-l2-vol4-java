# 05. 구현 노트 — 설계 대비 조정 사항

구현 중 실제 코드베이스 컨벤션에 맞추며 01~04 설계에서 조정한 부분을 기록한다. (설계 문서의 의도·정책은 그대로 유효하며, 여기서는 표현/배치만 바뀐 지점을 정리한다.)

> **⚠️ 2026-07-08 방류형 전환**: 아래 발급 관련 노트(§ ShedLock 순차실행+재큐잉, status 응답의 `maxActive/nextBatchSize`)는 정원제 기준이라 폐기됐다. 현행은 **방류형 + Lua 윈도우 레이트리밋**(발급 원자성·다중 인스턴스 안전을 `RedisTokenIssuer` Lua가 담당, ShedLock 없음), status 응답 필드는 `releaseSize/releaseIntervalSeconds`로 변경. 정설은 [`04`](./04-redis-model.md)·[`06`](./06-loadtest-ttl.md)·코드.

## 1. 인증 헤더 — `X-USER-ID` → `X-Loopers-LoginId/LoginPw`

- 01~04는 유저 식별을 `X-USER-ID` 헤더로 가정했으나, commerce-api의 실제 컨벤션은 `X-Loopers-LoginId` + `X-Loopers-LoginPw` 헤더 인증 → `UserFacade.authenticate()` → `userId`다. (`X-USER-ID`는 외부 pg-simulator 연동 전용 헤더였다.)
- 대기열 컨트롤러(`WaitingQueueV1Controller`)와 주문 컨트롤러 모두 이 기존 인증을 사용한다.
- **입장 토큰 전달 헤더 `X-QUEUE-TOKEN`(D4)** 은 설계대로 유지.

## 2. 진입 가드 — HandlerInterceptor → 주문 컨트롤러 인라인

- 02·03은 `QueueTokenGuard`를 `HandlerInterceptor`로 뒀으나, 이 코드베이스엔 인터셉터/필터가 전무하고 모든 전처리를 컨트롤러에서 직접 호출한다(예: `userFacade.authenticate`).
- 컨벤션 일관성 + 이미 확보한 `userId` 재사용을 위해, 가드를 `OrderV1Controller.placeOrder` **인라인**으로 구현했다: `waitingQueueFacade.validateEntry(userId, token)` → 주문 → `releaseEntry(userId, token)`.
- 토글: `waiting-queue.enabled=false`(기본)면 두 호출 모두 no-op이라 **기존 주문 흐름·테스트가 그대로 동작**한다. `true`(블랙프라이데이)에만 토큰을 요구한다.

## 3. 토큰 소모 시점 — "주문 완료" = placeOrder 성공

- 이 코드베이스에서 주문은 `placeOrder`가 **PENDING 생성 + 재고차감 + 쿠폰사용**(DB 부하 구간)이고, 결제 확정(PAID, `OrderService.markPaid`)은 PG 콜백으로 **비동기·후속**이다.
- 대기열은 DB 부하 관문이므로, 토큰을 **placeOrder 성공 시 소모**한다(활성 슬롯을 PG 지연에 묶지 않기 위해). 즉 "주문 완료"를 "주문(PENDING) 생성 성공"으로 해석했다. 이는 D2의 `avgProcessSeconds≈0.5s`(= placeOrder 처리 시간, PG 왕복 제외)와도 정합적이다.
- 주문 실패 시 토큰 미회수(TTL까지 재시도, D3)는 설계대로.

## 4. Admin 경로 — `/api/v1/admin/...` → `/api-admin/v1/waiting-queue/status`

- 기존 Admin 컨트롤러 컨벤션이 `/api-admin/v1/...`이라 이를 따랐다. 권한 체계는 다른 Admin API와 동일하게 미적용(운영 시 인가 선행 필요).
- 응답 필드: `queueSize, activeCount, releaseSize, releaseIntervalSeconds, throughputPerSecond, estimatedTailWaitSeconds`. (~~구: maxActive, nextBatchSize~~ → 방류형 노브로 교체)

## 5. 배치 발급 원자성 — Lua 미적용(단일 실행으로 대체)

- 04 §3.1의 Lua 원자화를 **채택**했다(초기 순차실행+ShedLock+재큐잉 → 2026-07-08 Lua로 승급, 이어서 방류형 전환). 현행 `RedisTokenIssuer`가 `윈도우예산 계산 → ZPOPMIN → pass/user-pass/active 발급 → 윈도우 INCRBY`를 단일 Lua로 원자 실행한다. **ShedLock 없이** 다중 인스턴스 방류 총합이 M초당 N으로 고정된다. `purgeExpiredAndCount/popFront/requeue` 등 순차실행 잔재는 제거됨.

## 6. EntryToken VO 미도입

- 03의 `EntryToken` VO(isExpired/ownedBy)는 만료·존재 판정을 Redis TTL/키 존재로 처리하면서 불필요해져 생략했다. 검증은 `WaitingQueueService.validateToken`이 Redis 조회 결과로 수행한다.

## 7. 정합성 읽기 — masterRedisTemplate 적용 확인

- 순서·활성 카운트·토큰 검증 어댑터(`RedisWaitingQueueRepository`, `RedisEntryTokenRepository`)는 `@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)`로 **마스터 읽기**를 사용(04 §0). 순번 캐시(`RankCache`)만 기본(REPLICA_PREFERRED) 템플릿 사용.

## 8. 테스트 현황

- 단위(mock) 17개 green: `ThroughputPolicyTest`(D2 산식 4), `WaitingQueueServiceTest`(멱등진입·상태판정·발급배치 back-pressure·재큐잉·토큰검증/소모 13).
- 통합(Testcontainers Redis) `WaitingQueueRedisIntegrationTest`: FIFO·멱등·발급배치·검증/소모·status를 실제 Redis로 검증. **Docker 기동 후 전수 green 검증 완료.** 주문 E2E도 green(대기열 의존성 추가 회귀 없음).
- 시뮬레이션 `WaitingQueueTtlSimulationTest`: TTL 스윕 부하 실험(→[`06`](./06-loadtest-ttl.md)). 결과로 **D1 토큰 TTL 60→30초 개정**.
