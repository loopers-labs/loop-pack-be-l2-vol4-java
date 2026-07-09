# 02. 시퀀스 다이어그램 — 이벤트 기반 아키텍처 (Event-Driven)

[`01-requirements.md`](./01-requirements.md) §6의 Step1~3 흐름을 레이어별 참여자 기준으로 시각화한다. 표기 규칙은 [`../week2/02-sequence-diagrams.md`](../week2/02-sequence-diagrams.md) §0을 따른다(레이어/화살표/생략/공통 에러). 이 문서의 결정 근거는 [`01-requirements.md`](./01-requirements.md) §9 결정사항 표를 따른다.

## 0. 참여자 (기존 레이어 + week7 신규)

### commerce-api (Producer)

| 약칭 | 클래스/컴포넌트 | 레이어 | 책임 |
| --- | --- | --- | --- |
| `OFac` | `OrderFacade` | Application | 주문 유스케이스 조립 |
| `OSvc` | `OrderService` | Domain Service | 주문 생성·상태 전이 |
| `PFac` | `PaymentFacade` | Application | 결제 시작/콜백/reconcile 조립 |
| `PCfm` | `PaymentConfirmer` | Application | 결제 확정 단위(콜백·reconcile 공유) |
| `LSvc` | `LikeService` | Domain Service | 좋아요 상태 전이 |
| `CFac` | `CouponFacade` | Application | 쿠폰 대고객 유스케이스 |
| `Pub` | `ApplicationEventPublisher` | Spring | 인-프로세스 도메인 이벤트 발행 |
| `Appender` | `OutboxAppender` | Application (@Transactional MANDATORY) | 도메인 트랜잭션 안에서 `outbox` INSERT + 커밋후 즉시발행 예약 |
| `OBox` | `outbox` 테이블 | DB | 발행 대기 메시지(PENDING/SENT/FAILED) |
| `ImmPub` | `OutboxImmediatePublisher` | Application (AFTER_COMMIT, REQUIRES_NEW) | 커밋 직후 즉시 Kafka 발행(저지연 주경로) → SENT 마킹. 실패분은 릴레이가 회수 |
| `Relay` | `OutboxRelay` | Application (@Scheduled+ShedLock) | PENDING 폴링 → Kafka 발행(즉시발행 안전망) → SENT 마킹 |
| `KT` | `KafkaTemplate` | Infra | Kafka 발행(acks=all, idempotence=true) |

### Kafka

| 토픽 | 키 | eventType 예 |
| --- | --- | --- |
| `catalog-events` | productId | LIKE_CHANGED, PRODUCT_VIEWED |
| `order-events` | orderId | ORDER_PAID |
| `coupon-issue-requests` | couponId | COUPON_ISSUE_REQUESTED |

### commerce-streamer (Consumer)

| 약칭 | 클래스/컴포넌트 | 책임 |
| --- | --- | --- |
| `MCon` | `ProductMetricsConsumer` (group=metrics-aggregator) → `MetricsAggregator` | catalog/order 이벤트 소비 → 집계 |
| `CCon` | `CouponIssueConsumer` (group=coupon-issuer) → `CouponIssuer` | 발급 요청 소비 → 발급 처리 |
| `Idem` | `EventHandledRepository` (컨슈머 인라인 조회-후-필터) | `event_handled(consumer_group, event_id)` 멱등 확인/기록 |
| `EH` | `event_handled` 테이블 | 처리 완료 표식(event_id PK) |
| `PM` | `product_metrics` 테이블 | 좋아요/판매량/조회 수 upsert |
| `CpnW` | `coupon` 테이블 | 원자 UPDATE(issued_count) |
| `UCW` | `user_coupon` 테이블 | 발급분 INSERT |
| `CIR` | `coupon_issue_request` 테이블 | 발급 결과(PENDING/ISSUED/SOLD_OUT/REJECTED) |

> **공통 envelope**: 모든 메시지는 `{ eventId, eventType, aggregateType, aggregateId, version, occurredAt, payload }`를 공유한다. `eventId`는 outbox PK(전역 고유) = 소비자 멱등 키. `version`은 envelope에 실려 있으나 가산 카운터라 현재 소비측 최신성 비교엔 쓰지 않는다(값 0, → S2-3).

---

## Step 1. ApplicationEvent 경계 분리

### S1-1. 결제 확정 시 이벤트 분리 (outbox append = BEFORE_COMMIT, Kafka 발행 = AFTER_COMMIT)

주요 로직(결제 확정 → 주문 PAID)과, 타 시스템(streamer 판매량 집계)이 필요로 하는 전파 이벤트를 분리한다. 전파 이벤트는 **도메인 변경과 같은 트랜잭션(BEFORE_COMMIT)에서 outbox에 적재**하고(원자성), 실제 Kafka 발행은 **커밋 후(AFTER_COMMIT)** 즉시발행 경로가 담당한다. (알림 같은 순수 부가 처리는 §9 #8에 따라 이번 범위 미구현 — 코드에 리스너 없음, mock/log 자리만 남김.)

```mermaid
sequenceDiagram
    participant PCfm as PaymentConfirmer
    participant OSvc as OrderService
    participant Pub as ApplicationEventPublisher
    participant L1 as OrderEventOutboxListener<br/>(BEFORE_COMMIT)
    participant Appender as OutboxAppender
    participant ImmPub as OutboxImmediatePublisher<br/>(AFTER_COMMIT)

    rect rgb(235,245,255)
    note over PCfm,Appender: 주요 트랜잭션 (TX) — 도메인 변경 + outbox INSERT 원자
    PCfm->>OSvc: markPaid(orderId)
    OSvc->>OSvc: 상태 PENDING→PAID (save)
    OSvc->>Pub: publishEvent(OrderPaidEvent)
    Pub-->>L1: onOrderPaid (BEFORE_COMMIT, 같은 TX)
    L1->>Appender: append(order-events, ORDER_PAID)
    Appender->>Appender: outbox INSERT(status=PENDING) + 커밋후 즉시발행 예약
    end

    rect rgb(235,255,235)
    note over ImmPub: 커밋 성공 후에만 실행 (REQUIRES_NEW)
    Pub-->>ImmPub: afterCommit → 방금 커밋된 outbox 행 즉시 Kafka 발행 → SENT
    end

    note over L1,ImmPub: append 실패는 주요 TX와 함께 롤백(원자성) — 유실/고아 이벤트 동시 차단.<br/>즉시발행 실패는 PENDING 유지 → 릴레이가 재발행(At-Least-Once)
```

> **판단 기준**: `OrderPaidEvent`는 (c) streamer 판매량 집계라는 **타 시스템 전파**가 필요 → Kafka(outbox) 대상. outbox 적재는 결제 확정과 (a) **원자적이어야** 하므로 BEFORE_COMMIT(같은 TX)이고, 실제 Kafka 발행은 커밋된 사실만 내보내야 하므로 AFTER_COMMIT 즉시발행 + 폴링 릴레이 안전망. 알림 등 전파 불필요·결과 무관 후속은 ApplicationEvent까지만(이번 미구현).

### S1-2. 좋아요 상태 전이 (결과적 일관성, 기존 일반화)

```mermaid
sequenceDiagram
    actor U as Client(User)
    participant LSvc as LikeService
    participant Pub as ApplicationEventPublisher
    participant LL as LikeEventOutboxListener<br/>(BEFORE_COMMIT)
    participant Appender as OutboxAppender

    U->>LSvc: like(userId, productId)
    rect rgb(235,245,255)
    note over LSvc,Appender: 주요 TX — product_like INSERT/activate + outbox 적재 원자
    LSvc->>LSvc: 실제 상태 전이 발생 시에만
    LSvc->>Pub: publishEvent(LikeChangedEvent +1)
    Pub-->>LL: onLikeChanged (BEFORE_COMMIT, 같은 TX)
    LL->>Appender: append(catalog-events, LIKE_CHANGED, delta=+1)
    end
    note over LL: 즉시 like_count 갱신 X → 커밋후 즉시발행+릴레이로 Kafka 전파 → 비동기 집계로 위임
```

> 상품 조회수(`PRODUCT_VIEWED`)도 동일 패턴이다: `getProductDetail` → `ProductViewedEvent` 발행 → `ProductViewOutboxListener`(BEFORE_COMMIT) → `append(catalog-events, PRODUCT_VIEWED)`.

---

## Step 2. Kafka 이벤트 파이프라인

### S2-1. Outbox 발행 (도메인 변경과 단일 트랜잭션)

핵심 불변식: **도메인 변경 INSERT/UPDATE 와 outbox INSERT 는 같은 트랜잭션**. 둘 다 커밋되거나 둘 다 롤백 → 메시지 유실(at-most-once) 제거.

```mermaid
sequenceDiagram
    participant L as BEFORE_COMMIT 리스너
    participant Appender as OutboxAppender
    participant OBox as outbox (DB)

    rect rgb(235,245,255)
    note over L,OBox: 도메인 변경과 동일 TX (@Transactional MANDATORY)
    L->>Appender: append(topic, eventType, key, payload)
    Appender->>OBox: INSERT (status=PENDING, eventId=PK)
    Appender->>Appender: 커밋후 즉시발행 예약(scheduleAfterCommit) — S2-2
    note right of OBox: 도메인 row + outbox row가<br/>원자적으로 커밋
    end
```

### S2-2. Outbox 발행 — 하이브리드(커밋후 즉시발행 + 폴링 릴레이 안전망)

발행은 두 경로다. **주경로**는 커밋 직후 `OutboxImmediatePublisher`가 방금 적재된 행을 즉시 발행(저지연). **안전망**은 `OutboxRelay`가 주기적으로 `PENDING`을 폴링해 즉시발행 실패분·앱 다운분을 재발행한다. 둘이 같은 행을 중복 발행해도 브로커 멱등(idempotence)·소비자 멱등(`event_handled`)이 흡수한다. 아래는 안전망 릴레이 흐름.

```mermaid
sequenceDiagram
    participant Sch as OutboxRelay<br/>(@Scheduled, ShedLock)
    participant OBox as outbox (DB)
    participant KT as KafkaTemplate
    participant K as Kafka

    loop 주기 폴링 (fixed-delay 1s)
        Sch->>OBox: SELECT * WHERE status=PENDING ORDER BY id ASC LIMIT 200
        OBox-->>Sch: PENDING 메시지 배치
        loop 각 메시지
            Sch->>KT: send(topic, key, envelope)
            KT->>K: produce (acks=all, idempotence=true)
            alt 전송 성공
                K-->>KT: ack
                Sch->>OBox: UPDATE status=SENT, sent_at=now
            else 전송 실패
                K-->>KT: error
                Sch->>OBox: PENDING 유지 (다음 주기 재시도 = At Least Once)
                note right of Sch: 반복 실패 시 FAILED 마킹 + 운영 알림
            end
        end
    end
    note over Sch: ShedLock으로 다중 인스턴스 중복 폴링 방지. 즉시발행이 대부분 이미 SENT 처리 → 평시 PENDING은 0~소수
```

### S2-3. 집계 Consumer — product_metrics 가산 UPDATE (멱등)

```mermaid
sequenceDiagram
    participant K as Kafka<br/>(catalog/order-events)
    participant MCon as ProductMetricsConsumer→MetricsAggregator<br/>(batch, manual ack)
    participant Idem as EventHandledRepository
    participant EH as event_handled (DB)
    participant PM as product_metrics (DB)

    K-->>MCon: poll batch(records)
    loop 각 record (한 트랜잭션)
        MCon->>Idem: findHandled(consumer_group, eventId)?
        Idem->>EH: SELECT WHERE consumer_group=? AND event_id IN (...)
        alt 이미 처리됨
            EH-->>Idem: 존재
            Idem-->>MCon: skip
        else 신규
            EH-->>Idem: 없음
            MCon->>PM: 측정값 가산 UPDATE (eventType별)
            note right of PM: LIKE_CHANGED → like_count = GREATEST(0, like_count+Δ)<br/>ORDER_PAID → sales_count += qty<br/>PRODUCT_VIEWED → view_count += 1
            MCon->>EH: INSERT (consumer_group, event_id) — 멱등 표식
        end
    end
    MCon->>K: manual ack(commit offset)
    note over MCon: 처리 성공 후에만 ack — 실패 시 재처리(At Least Once)
```

> **왜 version 비교가 없나**: 세 카운터가 전부 **가산(교환법칙 성립)**이라 순서에 무관하게 합산하면 같은 결과가 된다. 따라서 stale 판정용 `version` 비교가 불필요하고, 중복만 `event_handled(consumer_group, event_id)`로 차단하면 정확하다. (envelope의 `version` 필드는 미래의 비가산 집계 대비로 실려 있으나 현재 값 0, 소비측 미사용.)

### S2-4. reconcile 안전망 (이벤트 유실 보정)

```mermaid
sequenceDiagram
    participant Sch as ProductMetricsReconciler<br/>(@Scheduled cron)
    participant SRC as 원천 테이블<br/>(product_like, order_item)
    participant PM as product_metrics

    loop 주기 (0 */10 * * * *, 10분)
        Sch->>SRC: like_count = product_like COUNT, sales_count = PAID 주문 order_item 수량 SUM
        Sch->>PM: 카운터 보정 UPDATE (like/sales만)
    end
    note over Sch: 이벤트 유실/순서 이상에도 결국 정합(eventual). view_count는 원천 테이블이 없어 reconcile 제외
```

---

## Step 3. Kafka 기반 선착순 쿠폰 발급

### S3-1. 발급 요청 (API는 발행만, 202 Accepted)

```mermaid
sequenceDiagram
    actor U as Client(User)
    participant CCtrl as CouponV1Controller
    participant CFac as CouponFacade→CouponIssueRequestService
    participant CIR as coupon_issue_request (DB)
    participant Appender as OutboxAppender
    participant OBox as outbox (DB)

    U->>CCtrl: POST /api/v1/coupons/{couponId}/issue-requests
    CCtrl->>CFac: requestIssue(userId, couponId)
    rect rgb(235,245,255)
    note over CFac,OBox: 단일 TX
    CFac->>CFac: 사전검증(쿠폰 존재/기간) — 실패 시 4xx
    CFac->>CIR: findByUserIdAndCouponId(userId, couponId)  (check-first)
    alt 기존 요청 존재
        CIR-->>CFac: 기존 행
        CFac-->>CCtrl: 기존 requestId 반환(멱등, 이벤트 재발행 X)
    else 신규 접수
        CFac->>CIR: INSERT (status=PENDING, requestId)<br/>UNIQUE(user_id, coupon_id) = 최종 방어선
        CFac->>Appender: append(coupon-issue-requests, key=couponId)
        Appender->>OBox: INSERT (PENDING)
    end
    end
    CCtrl-->>U: 202 Accepted { requestId }
    note over U,CIR: 실제 발급은 비동기 — 결과는 S3-3로 조회.<br/>진짜 동시 중복은 UNIQUE 위반 예외로 전파(현재 별도 재조회 처리 없음)
```

### S3-2. 발급 처리 Consumer (원자 UPDATE + 멱등)

선착순 불변식 `issued_count <= total_quantity`를 어떤 동시성에서도 위반하지 않는다. key=couponId로 같은 쿠폰 요청이 같은 파티션에 직렬화된다.

```mermaid
sequenceDiagram
    participant K as Kafka<br/>(coupon-issue-requests)
    participant CCon as CouponIssueConsumer→CouponIssuer<br/>(batch, manual ack)
    participant Idem as EventHandledRepository
    participant Cpn as coupon (DB)
    participant UCW as user_coupon (DB)
    participant CIR as coupon_issue_request (DB)

    K-->>CCon: poll batch(envelopes: eventId, payload{requestId,couponId,userId})
    CCon->>Idem: findHandled(coupon-issuer, eventIds)
    note over CCon: 배치 내 eventId 중복 제거 + 이미 처리분 필터
    loop 신규 eventId (발급 TX 한 번)
        CCon->>Cpn: UPDATE coupon SET issued_count=issued_count+1<br/>WHERE id=? AND issued_count < total_quantity
        alt 영향 행 = 1 (수량 확보)
            CCon->>UCW: INSERT user_coupon (발급분)
            CCon->>CIR: UPDATE status=ISSUED
        else 영향 행 = 0 (수량 소진)
            CCon->>CIR: UPDATE status=SOLD_OUT
            note right of CIR: 예외 아님 — 재처리해도 발급 안 됨
        end
    end
    CCon->>Idem: markHandled(coupon-issuer, eventIds)
    CCon->>K: manual ack
```

> **1인 1매**: `coupon_issue_request`의 `UNIQUE(user_id, coupon_id)`(S3-1)가 요청 단계에서, `event_handled(consumer_group, event_id)`(event_id=outbox PK)가 소비 단계에서 이중 차단. `status=REJECTED`(자격 미달)는 enum에 정의만 되어 있고 **현재 미사용** — 컨슈머는 ISSUED/SOLD_OUT만 확정한다(확장 여지, 자격검증 추가 시 사용).

### S3-3. 발급 결과 조회

```mermaid
sequenceDiagram
    actor U as Client(User)
    participant CCtrl as CouponV1Controller
    participant CFac as CouponFacade
    participant CIR as coupon_issue_request (DB)

    U->>CCtrl: GET /api/v1/coupons/issue-requests/{requestId}
    CCtrl->>CFac: getIssueResult(userId, requestId)
    CFac->>CIR: SELECT status WHERE request_id=?
    CIR-->>CFac: PENDING | ISSUED | SOLD_OUT | REJECTED
    CFac-->>CCtrl: 결과
    CCtrl-->>U: 200 { status, (issued면 userCouponId) }
```

---

## 부록. 좋아요 토픽 마이그레이션 (스키마 통일)

기존 `catalog.like-changed.v1` → `catalog-events`(공통 envelope) 전환. 무중단 이전 절차. **(현재 코드 기준 이전 완료 — 구 토픽·`LikeCountConsumer`·`LikeChangedMessage`는 제거됨. 아래는 이력 참고용.)**

```mermaid
sequenceDiagram
    participant API as commerce-api
    participant Old as catalog.like-changed.v1
    participant New as catalog-events
    participant SOld as LikeCountConsumer(구, 제거됨)
    participant SNew as ProductMetricsConsumer(신)

    note over API,SNew: 1단계 — 신규 발행/소비 추가 (병행)
    API->>New: LIKE_CHANGED (신규)
    SNew->>SNew: catalog-events 소비 시작
    note over API,SOld: 2단계 — 구 토픽 발행 중단
    API--xOld: 발행 중단
    SOld->>SOld: 잔여 메시지 소진 후 폐기
    note over SNew: 3단계 — catalog-events 단독 운영
```

> reconcile(S2-4)이 원천에서 카운트를 재보정하므로, 이전 중 짧은 불일치는 결국 수렴한다.
