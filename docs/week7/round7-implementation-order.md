# Round 7 — 구현 순서 (다음 세션 착수용 핸드오프)

이 문서 하나만 읽고도 다음 세션에서 바로 이어서 구현할 수 있도록 **무엇을 어떤 순서로** 할지 정리한다. 판단 근거는 아래 두 문서에 있으니 필요할 때 참조한다.

- **[step1-application-event-boundary.md](./step1-application-event-boundary.md)** — 이벤트 분리 판단 축(A/B/C), phase 선택, outbox 표준형
- **[round7-event-application-map.md](./round7-event-application-map.md)** — 체크리스트→적용 지점 매핑, 신규 추가물, **패키지 배치 규칙(§1.2)**

## 0. 큰 그림 — 왜 이 순서인가

의존성상 **Step 1 → Step 2 → Step 3** 이 강제된다. Step 2가 Kafka 인프라를 깔고, Step 3가 그걸 실전(선착순 쿠폰)에 쓴다. Step 1은 인프라가 전혀 필요 없어 **지금 바로 착수 가능**하다.

```
Phase 1 (Step 1)  이벤트 경계 — 인프라 0, in-process ApplicationEvent
   └─▶ Phase 2 (Step 2)  Kafka 파이프라인 — producer/consumer/streamer/metrics
          └─▶ Phase 3 (Step 3)  선착순 쿠폰 — Kafka 실전 적용
```

## 1. 시작 전 이미 확정된 규칙 (재논의 불필요)

다음 세션에서 이걸 다시 논쟁하지 말 것. 이미 결정됐다.

1. **두 개의 필터를 분리한다** (map §0). "이벤트로 분리?(A/B/C)"와 "Kafka로 전파?(시스템 간)"는 다른 질문이다. **캐시 무효화는 이벤트(C)지만 Kafka가 아니다** — Step 1 이벤트를 전부 Kafka로 밀지 말 것.
2. **fact vs command** (step1 §2.1·§2.3 노트). 이벤트는 "이미 일어난 사실"(과거형)을 발행한다. `ProductCacheEvictEvent`(evict 지시)·`LikeCountChangedEvent`(발행 시점엔 카운트 미변경 + `outboxId` 누출)는 command 성이라 fact로 리네이밍한다.
3. **패키지 배치 = `apps/pg-simulator` 표준** (map §1.2). payment 도메인이 이미 이 구조다.

| 요소 | 위치 | pg-simulator 예시 |
|---|---|---|
| 이벤트 객체(fact) | `domain/<도메인>/` | `domain/payment/PaymentEvent` |
| 퍼블리셔 **포트** | `domain/<도메인>/` | `domain/payment/PaymentEventPublisher` (interface) |
| 퍼블리셔 **구현** | `infrastructure/<도메인>/` | `infrastructure/payment/PaymentCoreEventPublisher` (Spring `ApplicationEventPublisher` 래핑) |
| 리스너 | `interfaces/event/<도메인>/` | `interfaces/event/payment/PaymentEventListener` (얇게, app service 위임) |
| 릴레이/전송 포트+구현 | `domain/` + `infrastructure/` | `PaymentRelay` + `PaymentCoreRelay` |

   - **DIP**: 도메인/발행 지점은 Spring API를 모른다. 포트로 발행하고 `ApplicationEventPublisher`는 infra 구현에만 등장.
4. **도메인 이벤트는 domain Service 안에서 발행한다 — Facade에서 발행하지 않는다.** 원래 `OrderCreatedEvent`는 `OrderFacade.createOrder()`에서, `LikeChangedEvent`는 `LikeFacade.like/unlike()`에서 발행했으나, "도메인 이벤트는 도메인 서비스 안에서 발행되어야 한다"는 규칙에 따라 각각 `OrderService.create()`·`LikeService.register()/cancel()` 내부로 옮겼다. Facade는 여러 도메인을 조합만 하고, 이벤트 발행 자체는 그 이벤트가 속한 단일 도메인의 Service가 책임진다.
   - **리스너는 얇게**: 이벤트를 풀어 service 호출만. 비즈니스 로직(집계·상태 변경)을 리스너 본문에 두지 않는다.
   - **아웃박스 기록/릴레이는 producer-side**(origin 도메인), **소비 반응(캐시·집계·알림·로깅)은 반응 도메인**.

## 2. Phase 1 — Step 1 (여기부터 시작)

인프라 0. 순수 리팩터링 + 미구현 이벤트 추가. **작은 것 → 큰 것** 순서.

### ▶ 작업 1-1. 이벤트 이름 정리 리팩터링 — **완료**

동작을 바꾸지 않는 리네이밍 + 구조 정리라 위험이 낮고, Step 2 집계가 여기에 리스너만 얹으면 되도록 길을 낸다.

**(a) `createOrder`: `ProductCacheEvictEvent` → `OrderCreated`(fact) — 완료**

| 할 일 | 대상 | 상태 |
|---|---|---|
| `OrderCreated` fact 이벤트 신규 | `domain/order/OrderCreatedEvent`(orderId/userId/items + `productIds()` 헬퍼) | ✅ |
| 퍼블리셔 포트 신규 | `domain/order/OrderEventPublisher` (interface) | ✅ |
| 퍼블리셔 구현 신규 | `infrastructure/order/OrderCoreEventPublisher` (Spring `ApplicationEventPublisher` 래핑) | ✅ |
| `OrderFacade`가 포트로 발행하도록 변경 | `application/order/OrderFacade.java:78` — `orderEventPublisher.publish(OrderCreatedEvent.from(saved))` | ✅ |

`OrderCreatedEvent`는 캐시 무효화 전용이 아니라 **주문–결제 부가 로직 분리**라는 별도 목적으로 만든 것이라 (d)의 캐시 이벤트 정리 대상에서 제외했다 — 현재 구독 리스너는 없지만 Step 2 판매량 집계가 여기에 리스너만 얹을 예정(map §1.1, §2.1)이라 발행은 그대로 유지한다.

**(b) 좋아요 outbox를 표준형(①기록/②fast-path/③릴레이)으로 전환 — 완료**

| 할 일 | 대상 | 상태 |
|---|---|---|
| T1 트리거 이벤트 신규 | `domain/like/ProductLikedEvent(productId)`·`ProductUnlikedEvent(productId)` — 좋아요 눌림/취소 자체가 진짜 사실(map §2.5 원래 의도)이라 이 이름으로 확정. `LikeService.register()/cancel()`이 도메인 쓰기 직후 발행(규칙 4 — Facade가 아니라 domain Service에서 발행) | ✅ |
| ① outbox 기록 리스너 | `interfaces/event/like/LikeOutboxEventListener.record(ProductLikedEvent)`/`record(ProductUnlikedEvent)` — `@TransactionalEventListener(BEFORE_COMMIT)`, 주 트랜잭션과 같은 Tx에 합류 | ✅ |
| ② fast-path 리스너 | 같은 클래스 `.send(ProductLikedEvent)`/`.send(ProductUnlikedEvent)` — `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`, `likeOutboxProcessor.process()` 즉시 재호출(best-effort) | ✅ |
| ③ 릴레이 | `LikeOutboxProcessor.process()`(`@Scheduled(fixedDelay=1000)`) — PENDING을 찾아 `LikeFacade.reflectLikeCountChange(outboxId, productId, eventType)` 직접 호출. `markDoneIfPending`으로 멱등이라 ②·③이 중복 처리해도 안전 | ✅ |
| `LikeCountChangedEvent` 삭제 | `application/like/LikeCountChangedEvent.java` — ③이 이벤트 발행 없이 직접 호출하는 구조로 바뀌며 완전히 죽은 코드가 됨 | ✅ 삭제 |

> **이름이 (c)에서 한 번 나왔다 사라진 `ProductLikedEvent`/`ProductUnlikedEvent`와 같지만 목적이 다르다.** (c)의 것은 캐시 무효화용으로 **T2(카운트 반영 후)** 에 `LikeFacade.reflectLikeCountChange()`가 발행하던 것이었고 삭제됐다. 지금 이건 **T1(등록 시점)** 에 `LikeService`가 발행하는 outbox 트리거다. 같은 이름을 재사용한 이유는 "좋아요 눌림/취소"라는 사실 자체는 T1에 이미 확정돼 있고, 그게 진짜 fact라는 게 원래 map §2.5의 판단이었기 때문 — 처음에 T1/T2를 헷갈려서 (c)로 갔다가, 캐시 리스너를 없애면서 이 이름을 원래 의도대로(T1) 되돌린 것.

**(c)/(d) 캐시 무효화 관련 이벤트·리스너 — 전부 제거, 추후 도입 목표로 보류 (판단 두 차례 번복 끝에 최종 결정)**

경위: ① `ProductCacheEvictEvent`(공용 command)를 유지 → ② 발행 지점별 fact(`BrandDeletedEvent`/`ProductDeletedEvent`/`ProductUpdatedEvent`/`ProductLikedEvent`/`ProductUnlikedEvent`)로 전부 전환 → ③ 좋아요 T1(등록)/T2(카운트 반영) 타이밍 문제로 리스너를 먼저 제거 → ④ **캐시 무효화를 위해서만 만들었던 이벤트들도 전부 삭제**. 지금 당장 필요한 기능이 아니라는 판단.

| 할 일 | 대상 | 상태 |
|---|---|---|
| 캐시 무효화 리스너 삭제 | `interfaces/event/product/ProductCacheEvictListener.java` | ✅ 삭제 |
| 캐시 무효화 전용으로 만든 fact 이벤트·포트·구현 전부 삭제 | `domain/brand/BrandDeletedEvent`·`BrandEventPublisher`, `infrastructure/brand/BrandCoreEventPublisher`, `domain/product/ProductDeletedEvent`·`ProductUpdatedEvent`·`ProductEventPublisher`, `infrastructure/product/ProductCoreEventPublisher`, `domain/like/ProductLikedEvent`·`ProductUnlikedEvent`(당시 버전, `LikeEventPublisher`에서 해당 오버로드 제거) | ✅ 삭제 — 단, 이 이름은 (b)에서 T1 outbox 트리거 용도로 다시 만들어짐(아래 노트 참조) |
| `BrandFacade.deleteBrand`/`ProductFacade.deleteProduct`/`updateProductForAdmin`/`LikeFacade.reflectLikeCountChange` | 이벤트 발행 호출 제거, 순수 도메인 쓰기만 수행 | ✅ |
| `ProductCacheStore.evictProduct`/`evictAll` | `infrastructure/product/ProductCacheStore.java` | **유지** — 인프라 유틸 자체는 살아있고 전용 테스트(`ProductCacheStoreIntegrationTest`)도 그대로. 나중에 무효화 리스너를 다시 만들 때 그대로 재사용 가능 |
| 캐시가 이제 어떻게 정합성을 맞추나 | `ProductCacheStore`의 TTL(`PRODUCT_TTL=5분`, `LIST_TTL=30초`) | 능동 무효화 없이 **TTL 만료로만** 정합성 회복 — 브랜드/상품 삭제·수정·좋아요 직후 최대 TTL만큼 캐시가 stale할 수 있음(허용) |
| 캐시 무효화를 전제로 했던 테스트 제거 | `BrandFacadeIntegrationTest.invalidatesProductCache_whenBrandWithProductsIsDeleted`, `ProductFacadeIntegrationTest.returnsUpdatedValue_whenCachedProductIsUpdated`/`throwsNotFoundException_whenCachedProductIsDeleted`, `LikeOutboxProcessorIntegrationTest.invalidatesProductCache_whenLikeIsProcessed` | ✅ 삭제 (더 이상 성립하지 않는 동작을 검증하던 테스트) |
| `OrderCreatedEvent`는 삭제 대상에서 제외 | `domain/order/OrderCreatedEvent` | **유지** — 캐시 무효화 전용이 아니라 주문–결제 분리가 원래 목적(a 참조) |

**추후 도입 시 참고**: 캐시 무효화를 다시 붙일 땐 좋아요 쪽 T1/T2 타이밍을 주의할 것 — "좋아요 눌림" 자체(T1, `ProductLikedEvent`/`ProductUnlikedEvent` 발행 시점)와 "좋아요 수가 실제로 반영됨"(T2, `reflectLikeCountChange` 이후)은 다른 시점이고, 캐시는 T2 이후에 지워야 의미가 있다. **지금의 `ProductLikedEvent`/`ProductUnlikedEvent`(T1, outbox 트리거)를 캐시 무효화 리스너가 그대로 구독하면 안 된다** — 카운트가 아직 안 바뀐 시점이라 지워봤자 무의미하다. 캐시 무효화용으로는 T2 시점에 별도 신호(예: `reflectLikeCountChange` 안에서 직접 캐시 무효화 호출, 또는 새 fact)가 필요하다.

**작업 1-1 (a)/(b)/(c)/(d) 전부 완료.**

**완료 기준**: `compileJava`/`compileTestJava` 통과 확인함. **`./gradlew :apps:commerce-api:test` 전체 실행은 아직 안 함** — 다음 세션에서 먼저 돌려볼 것.

### ▶ 작업 1-2. 유저 행동 로깅 이벤트 — 완료

- `domain/UserActivityEvent`(마커, `userId()` 하나만 추가 요구) — `DomainEvent`를 확장. `LikedEvent`/`UnlikedEvent`/`OrderCreatedEvent`가 이를 함께 구현해 **새 이벤트를 중복 발행하지 않고 재사용**한다(좋아요·주문).
- 조회(`GET /products/{id}`)·목록조회(`GET /products`, 클릭으로 간주)는 비로그인 호출이라 `userId=null`인 신규 이벤트 `domain/product/ProductViewedEvent`/`ProductListViewedEvent`로 발행.
- 리스너 `interfaces/event/activity/UserActivityLogListener` 하나가 `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` + `@Async`로 트랜잭션 유무와 무관하게 전부 처리(좋아요/주문=트랜잭션 있음, 조회/목록조회=트랜잭션 없음). 지금은 SLF4J 로깅만, Step 2의 `event_log` 적재로 대체될 자리.
- 판정: C(best-effort) — 분석용 기본값 확정, 별도 요구사항 없어 유실 허용.

**⚠ Step 2 진입 시 주의**: §3 순서 6번이 "조회수 집계용 `ProductViewedEvent` 신규"라고 적어뒀는데, **이미 위에서 만들어졌다.** Step 2에서는 새로 만들지 말고 기존 `ProductViewedEvent`가 `OutboxableEvent`도 함께 구현하도록 확장(+ 집계 리스너 추가)하면 된다 — `OrderCreatedEvent`가 로깅과 판매량 집계 양쪽에 리스너만 얹는 것과 같은 패턴.

### ▶ 작업 1-3. 결제 결과 이벤트 — 스코프 아님 (체크리스트에 없음)

`PaymentResultEvent`는 이전 세션에서 문서 작성자가 확장한 계획이었을 뿐, `round7-quests.md`의 실제 체크리스트 4항목엔 없다. 체크리스트 4항목이 이미 전부 충족되어 **Step 1은 이걸 만들지 않고 완료 처리한다.** 필요해지면(결제 성공/실패 알림 등 실제 요구사항이 생기면) 그때 `PaymentFacade.confirmResolved`에 `AFTER_COMMIT` + `ConfirmOutcome.result()` 분기로 추가한다(`AFTER_ROLLBACK` 아님 — step1 §2.2).

**Step 1 체크리스트 충족 확인 (`round7-quests.md`에 체크 완료)**: 주문–결제 부가 로직 분리 ✅ / 좋아요 처리·집계 분리 ✅ / 유저 행동 로깅 ✅ / 동작 주체·트랜잭션 상관관계 ✅(phase 매핑). **Step 1 완료.**

## 3. Phase 2 — Step 2 (Kafka 파이프라인)

### ⚠ 진입 전 결정할 것 (map §6)

- **`product_stats` 운명**: 폐기 vs commerce-api 로컬 read model 유지. 상품 조회가 좋아요 수를 즉시 보여줘야 하면 유지. **이걸 먼저 정하고 시작.**
- **`modules/kafka/kafka.yml` consumer value deserializer 오설정** 확인 (`value-serializer`로 잘못 들어갔을 가능성).

### 순서

1. **commerce-api producer 배선**: `modules:kafka` 의존성 추가(현재 없음) → 릴레이(`infrastructure/`)가 Kafka로 발행. producer `acks=all`, `enable.idempotence=true` 설정.
2. **토픽 설계**: `catalog-events`(key=`productId`) / `order-events`(key=`orderId`) / `coupon-issue-requests`(key=`couponId`). **partition key로 순서 보장** (좋아요 like→unlike 역순 방지, map §2.5).
3. **commerce-streamer 채우기** (현재 `DemoKafkaConsumer`뿐인 스켈레톤): domain/infra/application 계층 + 엔티티 신규.
   - `ProductMetricsModel`(`product_metrics`) — 좋아요·판매량·조회수 upsert
   - `EventHandledModel`(`event_handled`, `event_id` PK) — 멱등 처리
   - `EventLogModel`(`event_log`) — 원천 로그 (행동 로깅 적재)
   - `event_handled`(멱등, 제어) vs `event_log`(데이터) **분리** — map §2.3
4. **집계 consumer**: manual Ack + `event_handled` 멱등 + `updated_at`/`version` 최신만 반영 → `product_metrics` upsert.
5. **좋아요 집계를 Kafka로 이관**: like outbox는 유지, 릴레이 발행 대상만 in-JVM → Kafka. `increaseLikeCount`가 streamer consumer로 이동 (map §2.2).
6. **판매량·조회수 집계 신규**: 판매량은 `OrderCreated`에 **리스너만 추가**(별도 이벤트 불필요, map §2.1). 조회수는 Step 1에서 이미 만든 `ProductViewedEvent`(`domain/product/`)에 `OutboxableEvent` 구현을 추가하고 집계 리스너를 얹는다 — 새 이벤트 클래스를 만들지 않는다.

## 4. Phase 3 — Step 3 (선착순 쿠폰)

현재 `CouponFacade.issue`는 동기 트랜잭션(A). "API는 요청만 발행 → consumer가 실제 발급"으로 재배치.

1. `CouponFacade.requestIssue` 신규 → `coupon-issue-requests` 발행, 즉시 202/요청ID 반환.
2. commerce-streamer Coupon consumer: 선착순 수량 제한 + 중복 방지. **동시성은 원자적 UPDATE 우선**(CLAUDE.md 전략) — `CouponTemplate` 잔여 수량 원자 감소, 또는 Redis 카운터. `IssuedCouponModel`은 이미 `@Version` + UK`(coupon_template_id, user_id)` 보유.
3. 발급 결과 확인 구조(polling/callback) + 상태 테이블.
4. 동시성 테스트 — 수량 초과 발급 없는지 검증.

## 5. 다음 세션 시작점 (TL;DR)

> **Step 1 완료.** 작업 1-1(이벤트 이름 정리)·1-2(유저 행동 로깅) 모두 끝났고, `round7-quests.md` Step 1 체크리스트 4항목 전부 체크됨. 1-3(결제 결과 이벤트)은 체크리스트에 없는 항목이라 스코프 아님으로 확정, 만들지 않음. 캐시 무효화 리스너는 (d)에서 제거하고 추후 도입 목표로 보류했다 — fact 이벤트 발행 자체는 그대로 살아있으니 나중에 리스너만 새로 붙이면 된다.
>
> **다음은 Step 2(Kafka 파이프라인) 진입.** 시작 전 §3 "진입 전 결정할 것"부터: (1) `product_stats` 폐기 vs 유지, (2) `modules/kafka/kafka.yml` consumer deserializer 오설정 확인. 조회수 집계는 새 이벤트를 만들지 말고 기존 `ProductViewedEvent`를 확장할 것(§2 작업 1-2 노트 참조). 전체 테스트(`./gradlew :apps:commerce-api:test`)는 Testcontainers 기반이라 로컬에서 임의 실행하지 않는다 — 필요하면 사용자가 직접 실행.
