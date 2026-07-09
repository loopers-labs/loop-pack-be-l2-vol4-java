# 01. 요구사항 명세 — 이벤트 기반 아키텍처 (Event-Driven Architecture)

## 0. 이 문서의 역할

week6까지 정의한 Loopers 커머스(상품·좋아요·주문·결제·쿠폰) 위에 **이벤트 기반 경계 분리**를 도입한다. 핵심 흐름(주문/결제/쿠폰)과 부가 흐름(로깅/알림/집계)을 분리하고, 시스템 간 전파가 필요한 이벤트는 Kafka로 옮겨 `commerce-api`와 수집·집계 애플리케이션을 디커플링한다.

이 문서는 요구사항·정책만 명세한다. 후속 설계 문서로 분리한다.
- 시스템 내·외부 행동 흐름 → [`02-sequence-diagrams.md`](./02-sequence-diagrams.md)
- 도메인 객체·이벤트 모델 → [`03-class-diagram.md`](./03-class-diagram.md)
- 데이터 모델(outbox/멱등/집계 테이블) → [`04-erd.md`](./04-erd.md)

기존 도메인 정의는 [`../week2`](../week2/01-requirements.md)~[`../week6`]를 따른다. 이 문서는 그 위에 추가·변경되는 부분(이벤트 발행/소비, 집계 테이블, 쿠폰 발급 파이프라인)만 명세한다.

> **기존 자산 (week5에서 구현됨)**: 좋아요 변경은 이미 `LikeChangedEvent`(ApplicationEvent) → `@TransactionalEventListener(AFTER_COMMIT)` → Kafka 토픽 `catalog.like-changed.v1` → `commerce-streamer`의 `LikeCountConsumer`(배치+manual ack) 경로로 비동기 집계되고 있었다. week7은 이 패턴을 **일반화**하고 **At Least Once + 멱등**으로 강화하며, 다른 도메인(주문/결제/조회)과 선착순 쿠폰으로 **확장**한다. **(week7 구현 결과: 발행 경로를 Transactional Outbox로 바꾸면서 outbox 적재는 `BEFORE_COMMIT` 리스너로, 실제 Kafka 발행은 커밋 후 즉시발행+릴레이로 이전했고, 토픽은 `catalog-events`로 통일해 구 토픽·`LikeCountConsumer`는 제거했다 — 아래 FR·`02` 참조.)**

---

## 1. 개요

지금까지 주문·결제·쿠폰 로직은 하나의 트랜잭션 안에서 "주요 로직 + 부가 로직"이 섞여 있었다. 부가 로직(유저 행동 로깅, 알림, 메트릭 집계)은 주요 비즈니스의 성공/실패와 강하게 묶일 이유가 없고, 동기 실행 시 응답 지연·장애 전파의 원인이 된다.

week7의 목표는 세 단계다.

1. **경계 분리** — "이 로직을 이벤트로 빼야 하는가?"를 판단해, 부가 로직을 Spring `ApplicationEvent`로 분리한다. 트랜잭션 결과와의 상관관계에 따라 적절한 리스너 phase를 선택한다.
2. **시스템 간 전파** — 분리한 이벤트 중 다른 애플리케이션이 소비해야 하는 것을 Kafka로 발행한다. Producer는 **Transactional Outbox**로 At Least Once를 보장하고, Consumer는 집계(좋아요 수/판매량/조회 수)를 `product_metrics`에 멱등하게 upsert한다.
3. **실전 적용** — 선착순 쿠폰 발급을 Kafka 파이프라인으로 구현한다. API는 발급 요청을 발행만 하고, Consumer가 수량 제한(동시성 제어) 하에 실제 발급을 처리한다.

> **학습 포인트가 아니라 운영 관점**: "무조건 이벤트로 분리"가 아니라, 경계 판단 기준 자체가 산출물이다. 잘못 분리하면 일관성·디버깅 비용이 커지므로, 각 이벤트마다 *왜 분리하는지*와 *어떤 일관성 모델을 택하는지*를 명시한다.

---

## 2. 범위 (Scope)

### In scope

- **Step 1 — ApplicationEvent 경계 분리**
  - 주문·결제 플로우에서 부가 로직(유저 행동 로깅, 알림 등)을 도메인 이벤트로 분리.
  - 좋아요 집계에 eventual consistency 적용(기존 자산 일반화).
  - 트랜잭션 상관관계에 따른 리스너 phase 선택(`@TransactionalEventListener` AFTER_COMMIT / AFTER_ROLLBACK 등).
- **Step 2 — Kafka 이벤트 파이프라인**
  - `commerce-api` → Kafka → 수집 애플리케이션(`commerce-streamer`, 명세상 "collector" 역할) 구조.
  - Producer: **Transactional Outbox Pattern** 기반 At Least Once 발행(acks=all, idempotence=true).
  - Consumer: 좋아요 수 / 판매량 / 조회 수를 `product_metrics`에 upsert. manual ack + 멱등 처리 + 최신 이벤트만 반영.
- **Step 3 — Kafka 기반 선착순 쿠폰 발급**
  - 발급 요청 API는 Kafka에 발행만 하고, Consumer가 실제 발급.
  - 선착순 수량 제한(예: 100명)에 대한 동시성 제어.
- 토픽 설계: `catalog-events`, `order-events`, `coupon-issue-requests`.
- 멱등 테이블 `event_handled`(또는 Redis), outbox 테이블, `product_metrics` 집계 테이블.

### Out of scope

- 신규 비즈니스 도메인 추가(회원/브랜드/상품 CRUD 등 기존 기능 변경 없음).
- 실시간 스트리밍 분석(Kafka Streams/ksqlDB) — 단순 Consumer 집계로 한정.
- 정확히-한-번(Exactly Once Semantics) Kafka 트랜잭션 — **At Least Once + 소비자 멱등**으로 등가 효과를 낸다.
- 알림 채널의 실제 구현(SMS/Push/Email 연동) — 이벤트 발행·소비 경계까지만. 실제 발송은 mock/log로 대체.
- 쿠폰 *사용/할인* 로직 변경(week3 정의 유지) — 이번엔 *발급* 경로만 비동기화.

---

## 3. 용어 정의 (Glossary)

- **주요 로직 / 부가 로직** — 주요 로직은 트랜잭션이 반드시 함께 성공/실패해야 하는 핵심(주문 생성, 재고 차감, 결제 확정). 부가 로직은 핵심 결과에 영향을 주지 않는 후속(로깅, 알림, 메트릭 집계).
- **ApplicationEvent** — Spring `ApplicationEventPublisher`로 발행되는 인-프로세스 이벤트. 같은 JVM 내 리스너가 소비. 시스템 경계를 넘지 않는다.
- **Transactional Outbox** — 도메인 상태 변경과 "발행할 메시지"를 **같은 DB 트랜잭션**으로 기록하고, 별도 릴레이가 outbox를 읽어 Kafka로 전송하는 패턴. 메시지 유실(at-most-once) 위험을 제거하고 At Least Once를 보장한다.
- **At Least Once** — 메시지가 최소 한 번은 전달됨(중복 가능). 중복은 소비자 멱등으로 흡수한다.
- **멱등 처리(Idempotency)** — 같은 이벤트를 여러 번 받아도 결과가 한 번 처리한 것과 동일. `event_handled(event_id)` 기록으로 중복 소비를 차단한다.
- **product_metrics** — 상품별 집계 카운터(좋아요 수/판매량/조회 수)를 모은 읽기 최적화 테이블. 이벤트 소비로 갱신되는 결과적 일관성 뷰.
- **eventual consistency(결과적 일관성)** — 집계 값이 즉시가 아니라 짧은 지연 후 정합해지는 모델. 핫 로우 경합을 피하기 위해 좋아요/조회 카운트에 적용.
- **선착순 발급(FCFS)** — 한정 수량 쿠폰을 요청 순서대로 발급하고, 한도 도달 시 이후 요청은 실패시키는 정책.

---

## 4. 액터 & 인증 (Actors & Auth)

| 액터 | 정체 | 이번 범위에서 할 수 있는 것 | 인증 |
| --- | --- | --- | --- |
| **User** | 가입한 회원 | 좋아요/주문/결제/상품조회(부가 이벤트 발생 주체), 선착순 쿠폰 발급 요청 | 기존 사용자 식별 헤더(week2 규약) |
| **Guest** | 비회원 | 상품 조회(조회 수 이벤트는 발생 가능, 발급/주문 불가) | 없음 |
| **Admin** | 운영자 | 선착순 쿠폰 캠페인 등록(수량/기간), 집계/발급 현황 조회 | 관리자 인증(기존 규약) |
| **commerce-api** | 발행 애플리케이션 | 도메인 이벤트 발행 + outbox 기록 | 내부 |
| **commerce-streamer** | 수집/집계 애플리케이션 | Kafka 소비, `product_metrics` 갱신, 쿠폰 발급 처리 | 내부 |

- 시스템 간 인증은 범위 외(내부 네트워크 가정). 토픽 접근 제어/ACL은 다루지 않는다.

---

## 5. 도메인 모델 개요

이번에 추가/변경되는 모델만 나열한다. 기존 도메인(User/Product/Like/Order/Payment/Coupon)은 week2~6 정의를 따른다.

- **OutboxMessage** — 발행 대기 메시지. 키 속성: `id`, `aggregate_type`(예: order, like, product), `aggregate_id`, `event_type`, `topic`, `partition_key`, `payload`(JSON), `status`(PENDING/SENT/FAILED), `created_at`, `sent_at`. 도메인 상태 변경과 같은 트랜잭션에 INSERT된다.
- **EventHandled (멱등 레코드)** — 소비 완료 표식. 키 속성: `event_id`(PK), `consumer_group`/`handler`, `handled_at`. DB 테이블 또는 Redis SET으로 구현. 동일 `event_id` 재수신 시 스킵.
- **ProductMetrics** — 상품 집계 카운터. 키 속성: `product_id`(PK 또는 unique), `like_count`, `sales_count`, `view_count`, `last_event_at`/`version`(최신 이벤트만 반영하기 위한 기준), `updated_at`. 상품과 1:1.
- **CouponIssueRequest (영속 엔티티 + 메시지)** — 선착순 발급 요청. 키 속성: `request_id`(PK·멱등 키), `coupon_id`(템플릿), `user_id`, `status`(PENDING/ISSUED/SOLD_OUT/REJECTED), `requested_at`/`processed_at`, `UNIQUE(user_id, coupon_id)`. **`coupon_issue_request` 테이블로 영속**(§9 #6)되어 비동기 결과 조회의 백킹 스토어 겸 1인1매 멱등 방어선이 되며, 동시에 payload가 Kafka `coupon-issue-requests` 토픽으로 발행된다. 실제 발급분은 기존 `user_coupon`에 남는다.
- **(변경) Coupon 템플릿** — 선착순을 위해 `total_quantity`(한정 수량), `issued_count`(발급 누계) 속성이 필요. 기존 템플릿에 확장.
- **(신규/도출) 도메인 이벤트들** — `LikeChangedEvent`(기존), `OrderPaidEvent`(결제 확정 시 판매량 집계용), `ProductViewedEvent`(조회 수), `UserActivityLoggedEvent`(부가 로깅), `CouponIssueRequestedEvent`(발급 요청). 구체 필드는 `03-class-diagram.md`에서 확정.

---

## 6. 기능 요구사항 (Functional Requirements)

### Step 1 — ApplicationEvent 경계 분리

#### FR-1.1 부가 로직 이벤트 분리 (주문/결제)

- **What**: 주문 생성·결제 확정의 부가 처리(유저 행동 로깅, 알림 발송 트리거)를 동기 호출에서 `ApplicationEvent` 발행으로 분리한다.
- **Rules** *(구현 반영)*:
  - **Kafka 전파용 이벤트의 outbox 적재는 도메인 변경과 원자적이어야 하므로** `@TransactionalEventListener(phase = BEFORE_COMMIT)`로 같은 트랜잭션에 적재한다. append 실패는 주요 트랜잭션과 함께 롤백돼야 정상(고아 이벤트/유실 동시 차단) — 즉 이 리스너는 격리 대상이 아니라 원자성 대상이다.
  - 실제 Kafka 전송은 커밋된 사실만 내보내도록 **커밋 후(AFTER_COMMIT) 즉시발행 + 폴링 릴레이**가 담당한다(→ Step2). 전송 실패는 outbox PENDING 유지로 회수.
  - 알림 등 **전파 불필요·트랜잭션 결과 무관** 부가 처리는 ApplicationEvent(AFTER_COMMIT)까지만 두는 게 원칙 — 리스너 실패는 주요 흐름에 전파하지 않는다. 단 이번 구현 범위(§9 #8)에서는 별도 알림/로깅 리스너를 두지 않았다(**미구현**, mock/log 자리만).
  - 트랜잭션 *실패 시*에만 의미 있는 처리(실패 로깅/보상)는 `AFTER_ROLLBACK`/별도 핸들러 대상 — 현재 결제 실패 보상은 콜백/reconcile 경로(week6)가 담당.
  - 발행은 도메인/서비스 계층에서, Kafka 전송 같은 외부 I/O는 리스너/발행기(인프라 어댑터)에서. 도메인은 Kafka를 모른다.
- **판단 기준(산출물)**: 각 이벤트에 대해 (a) 핵심 트랜잭션과 원자적이어야 하는가? → 같은 트랜잭션 유지, (b) 결과에 영향 없는 후속인가? → 이벤트 분리, (c) 다른 시스템이 필요로 하는가? → Step 2의 Kafka 전파 대상. 이 분류표를 문서화한다.
- **Errors**: 리스너 예외는 격리(로그)하고 주요 흐름에 전파하지 않는다. AFTER_COMMIT 리스너의 외부 호출 실패는 재시도/outbox로 회수한다.

#### FR-1.2 좋아요 집계 결과적 일관성 (기존 일반화)

- **What**: 좋아요 변경을 즉시 카운트 갱신이 아니라 이벤트 → 비동기 집계로 처리(week7에서 `catalog-events` 토픽으로 통일, → FR-2.3).
- **Rules**: 실제 상태 전이가 일어난 경우에만 이벤트 발행(중복 카운트 방지). 핫 로우 경합을 줄이기 위해 소비자 측 델타 코얼레싱 유지.

### Step 2 — Kafka 이벤트 파이프라인

#### FR-2.1 Producer — Transactional Outbox 발행

- **What**: 시스템 간 전파가 필요한 도메인 이벤트를 outbox 테이블에 도메인 변경과 같은 트랜잭션으로 기록하고, 릴레이가 Kafka로 발행한다.
- **Rules**:
  - 도메인 상태 변경과 `outbox` INSERT는 **단일 트랜잭션**. 둘 다 커밋되거나 둘 다 롤백.
  - 발행은 **하이브리드**: 커밋 직후 즉시발행(저지연 주경로) + `status=PENDING` 폴링 릴레이(`@Scheduled`+ShedLock, 안전망) → Kafka 전송 → `SENT` 마킹. 전송 실패는 PENDING 유지로 재시도(At Least Once). 즉시발행/릴레이가 같은 행을 중복 발행해도 브로커·소비자 멱등이 흡수.
  - Producer 설정: `acks=all`, `enable.idempotence=true`, 적절한 `retries`.
  - 파티션 키: `catalog-events`=productId, `order-events`=orderId, `coupon-issue-requests`=couponId. 같은 키는 순서 보장.
  - 각 메시지는 전역 고유 `event_id`(예: outbox PK/UUID)를 포함 — 소비자 멱등 키.
- **Errors**: Kafka 다운 시 outbox에 적체(유실 없음). 릴레이는 백오프 재시도. 영구 실패는 `FAILED`로 표시하고 운영 알림.

#### FR-2.2 Consumer — 집계 upsert (`product_metrics`)

- **What**: `catalog-events`/`order-events`를 소비해 상품별 좋아요 수/판매량/조회 수를 `product_metrics`에 upsert한다.
- **Rules**:
  - **manual Ack**: 처리 성공 후에만 ack(commit). 처리 중 예외 시 ack하지 않아 재처리.
  - **멱등**: `event_handled(consumer_group, event_id)`로 중복 차단. 이미 처리한 event_id는 스킵 후 ack.
  - **가산 집계 → 최신성 비교 불필요**: 좋아요/판매량/조회 세 카운터가 전부 가산(교환법칙 성립)이라 순서에 무관하게 합산하면 같은 결과가 된다. 따라서 `version` stale 판정을 두지 않고, 중복만 `event_handled`로 막는다. (envelope의 `version` 필드는 미래의 **비가산 집계** 대비로 실려 있으나 현재 값 0·소비측 미사용.)
  - 좋아요는 델타 합산(±1, `GREATEST(0, ...)` 가드), 판매량은 결제 확정 이벤트당 수량 가산, 조회 수는 조회 이벤트당 +1.
  - reconcile(정합 보정) 배치를 안전망으로 유지(원천 데이터로 주기적 재계산).
- **Errors**: 역직렬화 실패/처리 불가 메시지는 DLT(Dead Letter Topic) 또는 에러 로그로 격리(무한 재시도로 파티션 멈춤 방지).

#### FR-2.3 토픽 설계

| 토픽 | 키 | 발행 이벤트(예) | 소비 목적 |
| --- | --- | --- | --- |
| `catalog-events` | productId | 좋아요 변경, 상품 조회 | 좋아요 수·조회 수 집계 |
| `order-events` | orderId | 주문 결제 확정 | 판매량 집계 |
| `coupon-issue-requests` | couponId | 쿠폰 발급 요청 | 선착순 발급 처리 |

> **결정**: 기존 토픽 `catalog.like-changed.v1`은 폐기하고, 좋아요 변경도 `catalog-events`로 **스키마 통일**해 발행한다. 모든 catalog 이벤트는 공통 envelope(`eventId`, `eventType`, `productId`, `occurredAt`, `version`, `payload`)를 공유하며, `eventType`(LIKE_CHANGED / PRODUCT_VIEWED)으로 분기한다. 기존 `LikeCountConsumer`는 신규 envelope를 읽는 `ProductMetricsConsumer`로 대체됐다(구 토픽·구 컨슈머 제거, 마이그레이션은 02 문서 부록에서 다룸).

### Step 3 — Kafka 기반 선착순 쿠폰 발급

#### FR-3.1 발급 요청 (API)

- **What**: 사용자가 선착순 쿠폰 발급을 요청하면, API는 검증 후 `coupon-issue-requests`에 메시지를 발행만 하고 즉시 "접수됨"을 응답한다(실제 발급은 비동기).
- **API**: `POST /api/v1/coupons/{couponId}/issue-requests` (User 인증)
- **Response(예)**: `202 Accepted`, 본문에 `requestId`(발급 결과 조회용).
- **Rules**:
  - 멱등: 같은 (userId, couponId)의 중복 요청은 한 번만 접수(요청 단계 또는 소비 단계에서 차단).
  - 동일 사용자 1인 1매 정책(템플릿 기준) — 소비 단계에서 최종 보장.
  - 발행 자체도 outbox로 At Least Once 권장(요청 유실 방지).
- **Errors**: 존재하지 않는/마감된 쿠폰은 즉시 4xx(발행 전 검증 가능 범위). 수량 소진은 비동기 처리이므로 발급 결과 조회로 확인.

#### FR-3.2 발급 처리 (Consumer)

- **What**: Consumer가 `coupon-issue-requests`를 소비해 수량 한도 내에서 실제 `user_coupon`을 발급한다.
- **Rules**:
  - **선착순 동시성 제어**: 한정 수량을 초과 발급하지 않는다. 후보 메커니즘 — (a) DB 원자적 증가(`UPDATE coupon SET issued_count = issued_count + 1 WHERE id=? AND issued_count < total_quantity`), (b) 비관적 락, (c) Redis 원자 카운터(DECR) 선점 후 DB 반영. 택1을 설계 문서에서 확정.
  - **멱등**: `event_handled(request_id)`로 같은 요청 중복 발급 차단. 1인 1매도 함께 검증.
  - 한도 초과 시 발급 실패로 마킹(예외 아님) 후 ack — 재처리해도 발급되지 않아야 한다.
  - 파티션 키=couponId로 동일 쿠폰 요청을 같은 파티션에 모아 경합 범위를 좁힌다.
- **Errors**: 수량 소진 → 해당 요청 "마감으로 실패". 사용자 자격 미달 → "발급 불가". 둘 다 결과 레코드로 남겨 조회 가능.

#### FR-3.3 발급 결과 조회

- **What**: 사용자가 자신의 발급 요청 결과(접수/발급완료/마감/실패)를 조회한다.
- **API**: `GET /api/v1/coupons/issue-requests/{requestId}` (User 인증) — *결과 저장 방식은 미해결 질문 참고*.

---

## 7. 비기능 요구사항 (Non-functional)

- **전달 보장**: 시스템 간 이벤트는 At Least Once(outbox + Kafka idempotence). 유실 0을 우선, 중복은 소비자 멱등으로 흡수.
- **멱등성**: 모든 Consumer는 `event_id`/`request_id` 기반 멱등. 재처리·재기동·리밸런싱에도 결과 불변.
- **일관성 모델**: 집계 카운터(좋아요/판매량/조회)는 결과적 일관성. 쿠폰 발급 수량은 **강한 일관성**(초과 발급 절대 불가).
- **동시성**: 선착순 발급은 한정 자원 경합 — 정확한 한도 보장이 1순위. 핫 로우 경합 완화를 위해 파티셔닝/원자 연산 사용.
- **순서**: 동일 키(productId/orderId/couponId)는 파티션 단위 순서 보장. 키 간 순서는 보장하지 않으며 집계는 교환법칙 성립(델타 합산)으로 순서 비의존 설계.
- **격리**: 부가 로직 장애가 주요 트랜잭션/응답에 전파되지 않음.
- **관측성**: outbox 적체, consumer lag, DLT 유입, 발급 성공/실패율을 모니터링.

---

## 8. 정책 / 비즈니스 규칙

- **경계 판단 규칙**: 핵심 트랜잭션과 원자적이어야 하면 분리하지 않는다. 결과 무관 후속이면 ApplicationEvent. 타 시스템 필요 시 Kafka(outbox). 이 3분류를 모든 후보 로직에 적용해 표로 남긴다.
- **outbox-도메인 원자성**: 도메인 변경 없는 메시지 발행 금지(고아 이벤트 방지), 메시지 없는 도메인 변경에서 전파 누락 금지 — 둘을 한 트랜잭션에 묶는다.
- **소비자 멱등 우선**: 중복은 정상으로 간주. 모든 핸들러는 재실행 안전하게 작성.
- **가산 집계 규칙**: 좋아요/판매량/조회 집계는 전부 가산(교환법칙)이라 순서 비의존 — stale 판정용 `version` 비교를 두지 않고 `event_handled` 멱등만으로 정확하다. (비가산 집계가 생기면 그때 `version`/`updated_at` 최신성 비교를 도입한다.)
- **선착순 한도 불변식**: `issued_count <= total_quantity`를 어떤 동시성 상황에서도 위반하지 않는다.
- **reconcile 안전망**: 이벤트 유실/순서 이상에 대비해 원천(소스 오브 트루스)에서 주기적 재집계.

---

## 9. 결정사항 (Decisions)

분석 단계에서 제기된 선택지에 대해 다음과 같이 확정한다. (트레이드오프는 02 이후 설계 문서에서 구체화)

| # | 항목 | 결정 | 근거 |
| --- | --- | --- | --- |
| 1 | 수집 앱 | 기존 **`commerce-streamer` 확장** (신규 collector 모듈 X) | Kafka·Testcontainers·배치/manual-ack 인프라 재사용. 패키지로 책임 분리(`consumer/metrics`, `consumer/coupon`, 각각 별도 consumer group) |
| 2 | 좋아요 토픽 | **스키마 통일** — 좋아요도 `catalog-events`로 발행, 기존 `catalog.like-changed.v1` 폐기 | 공통 envelope(`eventId/eventType/productId/occurredAt/version/payload`)로 일원화, `eventType`으로 분기. 토픽·소비자 단순화 |
| 3 | outbox 릴레이 | **하이브리드 — 커밋후 즉시발행 + 폴링 스케줄러(@Scheduled + ShedLock) 안전망** | week6 `PaymentReconcileScheduler` 패턴 재사용, 다중 인스턴스 안전. 즉시발행으로 저지연, 릴레이로 At-Least-Once 보장. Debezium은 인프라 과함 |
| 4 | 멱등 저장소 | **DB `event_handled` 테이블** (`event_id` PK) | 영속·조회·정합 보정 용이. consumer group별 처리 표식 |
| 5 | 선착순 동시성 | **DB 원자 UPDATE** (`UPDATE coupon SET issued_count=issued_count+1 WHERE id=? AND issued_count<total_quantity`) | couponId 파티션으로 직렬화 + 단일 원자 연산으로 초과 발급 불가. Redis 카운터 미도입 |
| 6 | 발급 결과 저장 | **별도 `coupon_issue_request` 테이블 영속** (PENDING→ISSUED/SOLD_OUT/REJECTED) | 비동기 결과 조회의 백킹 스토어 + `(user_id,coupon_id)` unique로 멱등 겸용 |
| 7 | 조회 수 집계 | `catalog-events`(eventType=PRODUCT_VIEWED)로 **샘플링 없이 +1** | product_metrics view_count 집계. 트래픽 우려 시 추후 샘플링/배치로 최적화 여지 |
| 8 | 알림/로깅 구현 수준 | **mock/log까지만** (경계 분리 증명 목적) | 실제 채널 연동은 범위 외 |
| 9 | DLT | **에러 로그 + 스킵 기본**, DLT는 추후 도입 여지 | 처리 불가 메시지로 파티션 정지 방지가 우선. 운영 성숙 시 DLT 추가 |

### 남은 가정 (Assumptions)

- 시스템 간 인증/토픽 ACL은 내부 네트워크 가정으로 다루지 않는다.
- outbox 폴링 주기·배치 크기, consumer 동시성 수 등 튜닝 값은 설계/구현 단계에서 확정한다.
