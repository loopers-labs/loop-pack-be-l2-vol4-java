# Step 1 — ApplicationEvent 로 경계 나누기 (설계)

- 작성일: 2026-07-01
- 대상 모듈: `apps/commerce-api` (base package `com.loopers`)
- 범위: Round 7 Step 1 (Spring ApplicationEvent 만 사용, Kafka 없음). Step 2 에서 이 이벤트들을 Kafka 로 승격.

## TL;DR

주문/좋아요 유스케이스의 **부가 후속 로직을 Spring ApplicationEvent 로 분리**한다. 핵심 트랜잭션(주문 저장·재고·쿠폰, 좋아요 저장)은 그대로 커밋 보장하고, 집계·데이터플랫폼 전송·행동 로깅은 커밋 이후 리스너로 뺀다. 리스너 실행 정책은 **후속의 성격에 맞춰 혼합**한다(빠른 로컬 집계=동기 AFTER_COMMIT, 외부·부가=@Async). 이벤트는 **도메인별 과거형 record**로 두어 Step 2 Kafka 토픽과 1:1 로 잇는다.

> **구현 개정 (2026-07-02, Option A) — slice A 재설계**
> 구현 중 발견: `product_like_count` 증가를 AFTER_COMMIT 리스너(REQUIRES_NEW)로 빼자 기존 `LikeConcurrencyTest`(즉시·정확 카운트)와 충돌했다. 동기 리스너의 REQUIRES_NEW 가 afterCommit 시점에 원래 커넥션과 겹쳐 **커넥션 풀 고갈 → 증분 유실**. 판단(과제의 "무조건 분리 아님"): **운영 카운트(`product_like_count`)는 API 가 즉시 정확해야 하므로 like 트랜잭션 안에서 원자적 upsert 로 유지**하고, `LikeAdded`/`LikeRemoved` 이벤트만 발행해 **로깅(Step 1) + 분석집계(Step 2 `product_metrics`)로 분리**한다. → `LikeCountListener` 제거. 아래 "알려진 갭"의 like_count 드리프트는 **해소됨**(카운트가 tx 안이라 정확). 나머지 슬라이스(B/C)는 설계대로.

## 배경 — 현재 코드 상태 (탐색 결과)

| 항목 | 현재 |
|---|---|
| 재고 차감 + 쿠폰 사용 | 이미 `OrderFacade.createOrder` 한 트랜잭션 안 (강한 일관성 — 유지) |
| 결제(PG) | 이미 별도 엔드포인트로 분리 완료 (week6: record-first + reconcile 폴링) |
| 포인트 / 알림 / 데이터플랫폼 | 미구현 (없음) |
| 좋아요 + 집계(`product_like_count`) | 현재 `LikeFacade.like` **한 트랜잭션**에서 `INSERT ... ON DUPLICATE KEY UPDATE` 로 원자 처리 |
| 이벤트 인프라 | commerce-api 에 전무 (`@EnableAsync` 없음, `modules:kafka` 미의존) |

→ 그래서 Step 1 의 실제 작업은 **(A) 좋아요–집계 분리 / (B) 주문 이벤트→데이터플랫폼 전송 / (C) 유저 행동 로깅** 세 슬라이스.

## 목표 / 비목표

**목표**
- 후속 로직을 이벤트로 분리하되, "이걸 이벤트로 뺄지"의 판단 기준을 코드로 드러낸다.
- 트랜잭션 결과와의 상관관계에 맞는 리스너(`@TransactionalEventListener` phase, `@Async`)를 선택한다.
- 집계 실패와 무관하게 좋아요는 성공하도록 만든다.
- Step 2(Kafka) 로 매끄럽게 이어질 이벤트 taxonomy 를 확정한다.

**비목표 (이번 슬라이스 아님)**
- Kafka / Outbox / 멱등 / DLQ (전부 Step 2).
- 판매량 정확 집계용 `OrderPaid` 이벤트 (결제 도메인 건드림 → Step 2).
- like_count 재계산(resync) 잡 (아래 "알려진 갭" 참조).

## 아키텍처

### 신규 인프라
- `CommerceApiApplication` 에 `@EnableAsync` 추가.
- `com.loopers.config.AsyncConfig` (신규): 이벤트 전용 `ThreadPoolTaskExecutor` 빈 `eventExecutor`(명시적 이름, 풀/큐/거부정책 설정) + `AsyncUncaughtExceptionHandler`(@Async void 리스너 예외를 SLF4J 로깅 — "예외 은닉" 대응). `@Async("eventExecutor")` 로 명시 참조하여 `@EnableScheduling` 풀과 분리.

### 이벤트 taxonomy (순수 record, Spring 의존 없음 → DIP 유지)
- `domain.like.event.LikeAdded(Long userId, Long productId, ZonedDateTime occurredAt)`
- `domain.like.event.LikeRemoved(Long userId, Long productId, ZonedDateTime occurredAt)`
- `domain.order.event.OrderPlaced(Long orderId, Long userId, long finalAmount, List<Line> lines, ZonedDateTime occurredAt)` — `Line(Long productId, int quantity)`
- `domain.product.event.ProductViewed(Long productId, Long userId /*nullable*/, ZonedDateTime occurredAt)`

**불변 규칙:** 이벤트는 **원시값 스냅샷만** 담는다. 엔티티/프록시 금지. 발행은 트랜잭션 안에서 값 전부 채운다 → 리스너(AFTER_COMMIT, tx 종료 후)의 `LazyInitializationException` 원천 차단.

### 발행 지점 (Facade 에 `ApplicationEventPublisher` 주입)
- `LikeFacade.like` (`@Transactional`): `likeCountRepository.increase(...)` **제거** → `publishEvent(new LikeAdded(...))`. LikeFacade 의 `LikeCountRepository` 의존 제거(결합 끊김). `unlike` 대칭(`LikeRemoved`).
- `OrderFacade.createOrder` (`@Transactional`): return 직전 `publishEvent(new OrderPlaced(...))` (트랜잭션 안 → AFTER_COMMIT 등록).
- `ProductFacade` 상세조회: `publishEvent(new ProductViewed(...))` (트랜잭션 없음). 캐시 히트 경로에서도 반드시 발행되도록 위치. userId 는 비로그인 시 null.

### 리스너 (혼합 정책)
| 리스너 (패키지) | 구독 | 실행 | 책임 |
|---|---|---|---|
| `LikeCountListener` (`application.like`) | LikeAdded / LikeRemoved | `@TransactionalEventListener(AFTER_COMMIT)` **동기** | `likeCountRepository.increase/decrease` (A) |
| `DataPlatformEventListener` (`application.dataplatform`) | OrderPlaced | `@Async @TransactionalEventListener(AFTER_COMMIT)` | `DataPlatformSender.send(payload)` (B) |
| `UserActionLogListener` (`application.useraction`) | LikeAdded / OrderPlaced (`@Async @TransactionalEventListener(AFTER_COMMIT)`), ProductViewed (`@Async @EventListener`) | @Async | 구조화 로깅 (C) |

- 한 이벤트를 여러 리스너가 구독(LikeAdded→집계+로깅, OrderPlaced→전송+로깅) = "한 이벤트, 여러 리스너".
- `UserActionLogListener` 는 마커 인터페이스 없이 **명시적 메서드**로 타입별 분기. Step 1 은 **로깅만**(UserAction 테이블/클래스 없음 — 과다 모델링 금지).

### seam (Step 2 대비)
- `domain.dataplatform.DataPlatformSender` (인터페이스) + `domain.dataplatform.DataPlatformPayload`.
- `infrastructure.dataplatform.LoggingDataPlatformSender` (Step 1 스텁: SLF4J 로깅). Step 2 에서 Kafka/Outbox 구현체로 교체. (`PgClient` seam 패턴과 동일한 결.)

### Repository 조정
- `LikeCountRepositoryImpl.increase/decrease` 에 `@Transactional` 추가 — 이제 리스너의 새 트랜잭션에서 단독 실행되므로(@Modifying 쿼리는 트랜잭션 필요). 별도 빈의 `@Transactional` 이라 리스너 `try/catch` 시 rollback-only 누수 없음.

## 데이터 흐름
- **A**: `like()` →〔tx: Like 저장 + LikeAdded 발행〕→ commit → LikeCountListener(동기) → likeCount upsert(새 tx)
- **B**: `createOrder()` →〔tx: 주문/재고/쿠폰 + OrderPlaced 발행〕→ commit → DataPlatformEventListener(@Async, 별도 스레드) → send
- **C**: 세 이벤트 → UserActionLogListener(@Async) 로깅 (ProductViewed 는 커밋 없이 즉시)

## 에러 처리

| 슬라이스 | 리스너 실패 시 | 사용자 응답 | 데이터 결과 |
|---|---|---|---|
| A (동기 AFTER_COMMIT) | `try/catch` 로그(fire-and-forget). `increase()`는 별도 빈 `@Transactional` 이라 깔끔히 catch | 좋아요 **성공**(이미 커밋) | like_count 드리프트 가능 — 알려진 갭 |
| B (@Async AFTER_COMMIT) | `AsyncUncaughtExceptionHandler` 로깅 | 주문 **성공** | 전송 누락(스텁이라 무해) |
| C (@Async) | 동일 핸들러 로깅 | 영향 없음 | 로그 누락 |

- 비즈니스 흐름(좋아요/주문)은 후속 실패와 무관하게 **항상 성공**한다.

### 알려진 갭 — like_count 드리프트 (정직한 기록)
- 발생: "커밋 ~ 동기 리스너 사이 크래시" 또는 "upsert 실패(예외 삼킴)" 시 like_count 가 실제 좋아요 수와 어긋남.
- **현재 복구 잡 없음.** `product_rank` 는 `product_like_count` 를 SELECT 해 rebuild 하므로 드리프트를 **고치지 못한다**(원천은 `likes` 테이블 COUNT). 슬라이스 A 는 원자적 in-tx upsert 대비 정합성 **다운그레이드**이며, 이는 "분리하면 갭이 생긴다"를 체감하기 위한 의도적 트레이드오프다.
- **닫는 시점**: Step 2. 이벤트를 Outbox+Kafka 로 옮기면 (a) durable → 유실 없음, (b) Consumer 가 `likes` 에서 재집계 가능. (선택적 하드닝: `likes` COUNT 기반 resync 스케줄 잡 — 이번 스코프 밖.)

## 테스트 전략 (TDD, Testcontainers MySQL)

**Level 1 — 단위 (Red-Green 주력)**
- 리스너 직접 호출: `new LikeCountListener(fakeRepo).onAdded(new LikeAdded(...))` → `increase` 호출 검증.
- 발행 검증: `@RecordApplicationEvents` + `ApplicationEvents` 로 "`like()` 시 LikeAdded 발행" 확인(리스너 비활성).
- `DataPlatformPayload.from(event)` 매핑 단위 테스트.

**Level 2 — 통합 (배선 + phase 의미 검증)**
- ⚠️ 테스트를 `@Transactional` 로 감싸지 않는다(롤백 시 AFTER_COMMIT 미발화). non-transactional + 수동 정리.
- `@Async` 는 `Awaitility.await().untilAsserted(...)` 로 대기.
- 핵심 케이스:
  1. `like()` 커밋 → likeCount 증가(eventually).
  2. `like()` 트랜잭션 롤백 유도 → likeCount **안 증가** (AFTER_COMMIT phase 증명).
  3. LikeCountListener 예외 → 좋아요 **성공** + 사용자 에러 없음 (격리 증명).
  4. `createOrder()` → 커밋 후 `DataPlatformSender.send` 호출(fake sender spy).
  5. 상세조회 → ProductViewed 발행 → 행동 로그(spy).

## 구현 슬라이스 & 커밋 분리 (TDD, 각 슬라이스 = 별도 커밋)
1. `feat:` 이벤트 인프라 (`@EnableAsync`, `AsyncConfig`, 이벤트 record 4종 뼈대).
2. `feat:` (A) 좋아요–집계 분리 — LikeFacade 발행 + LikeCountListener + Repo `@Transactional` + 테스트.
3. `feat:` (B) 주문 이벤트 → DataPlatformSender seam + LoggingDataPlatformSender + DataPlatformEventListener + 테스트.
4. `feat:` (C) 유저 행동 로깅 — ProductViewed 발행 + UserActionLogListener + 테스트.

## 미해결 / 확인됨
- D1: like_count 드리프트 → (b) Step 1 은 갭 감수 + 문서화, Step 2 에서 닫음. **확정.**
- D2: 판매량 이벤트 시점 → Step 1 은 `OrderPlaced`(생성)만, 판매량-정확 `OrderPaid`(markPaid)는 Step 2. **확정.**
