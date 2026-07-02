<!--
  GitHub Issue 제출용 (템플릿: 📐 Tech Note — Design Doc). 라벨: tech-note, format:design-doc
  아래를 이슈 폼의 각 필드에 그대로 붙여넣으세요.
-->

# 제목 (Title 필드)

[Design Doc] 이벤트 경계 설계 — 코드·트랜잭션·시스템, 세 층에 선 긋기 (7주차 · 6팀 · 김동원)

---

# TL;DR (필드)

모든 흐름을 한 트랜잭션에 넣던 커머스를 *"실패하면 되돌려야 하나?"* 라는 정합성 질문 하나로 나눴다. ApplicationEvent(같은 JVM) → Outbox+Kafka(시스템 간) → 순차 Consumer(선착순)로 층층이 분리했고, **동시 300명 요청에도 선착순 100장 초과발급 0**을 달성했다.

---

# 본문 (필드)

## Introduction & Goals

- **Context / Background**: 지금까지 재고 차감·쿠폰 사용·결제·집계·알림을 `createOrder()` 한 트랜잭션에 다 넣었다. 편하지만 넷이 *한 운명*이 된다 — 알림이 느리면 주문이 느려지고, 집계가 터지면 주문이 롤백된다. 트랜잭션이 길수록 락·커넥션 점유가 길어져 TPS도 떨어진다. 그래서 **나눠야 하지만, 아무거나 나누면 안 된다**(과제도 "무조건 이벤트 분리가 아니다"라고 못 박음). 나누는 순간 *강한 일관성(ACID 롤백)* 을 잃고 *최종 일관성(보상·재시도)* 을 떠안기 때문이다.
- **Goals**:
  1. 부가 로직(집계·전송·로깅)을 핵심 흐름에서 떼어 **장애 격리**.
  2. 시스템 경계를 넘는 이벤트를 **유실 없이(At-Least-Once)** 전달하고 **정확히 한 번** 반영.
  3. 선착순 대량 요청을 **초과발급 0**으로 안전 처리.

**설계의 척추 — 통찰 3가지**
- **① "분리"에는 층이 있다: 코드 결합 ≠ 트랜잭션 분리.** 기본 `@EventListener`는 발행 스레드에서 *동기로, 같은 트랜잭션 안에서* 돈다. 이벤트를 쐈다고 트랜잭션이 나뉘는 게 아니다 — 끊긴 건 컴파일 의존성뿐. 진짜 분리는 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`를 **명시적으로** 붙여야 한다.
- **② 분리 여부는 정합성 게이트, 분리 방식은 실행 시점.** *"실패하면 핵심을 되돌려야 하나?"* → YES면 같은 트랜잭션(재고·쿠폰), NO면 분리. 그리고 **정합성이 필요한 값 자체는 원자 연산에 남기고, "그 사실의 통지"만 이벤트로** 보낸다.
- **③ 경합은 중재하지 말고 제거하라.** 락은 "동시에 덤벼도 한 명씩 통과"(경합 심판 → 비용 잔존), 큐(Kafka)는 "애초에 줄 세움"(경합 소멸).

## Detailed Design

### System Architecture

**Step 1 — ApplicationEvent (같은 JVM):**
```
LikeFacade.like()  ─[한 트랜잭션]─
   ├ likeRepository.save(Like)
   ├ likeCountRepository.increase()      // 운영 카운트: tx 안, 즉시·정확
   └ publishEvent(LikeAdded)             // 사실 통지만
   ── commit ──
        └ (AFTER_COMMIT/@Async) 데이터플랫폼 전송 · 행동 로깅 리스너
```
리스너 정책은 **성격별 혼합** — 집계=동기, 외부 전송/로깅=@Async, 읽기 경로(ProductViewed)=커밋이 없어 `@EventListener`.

**Step 2 — Outbox → Kafka (시스템 간, At-Least-Once + 멱등):** DB 커밋과 Kafka 발행은 한 트랜잭션으로 못 묶는다(dual-write). 그래서 원자성은 *outbox 행 INSERT*에 건다.
```
[commerce-api] 도메인 변경 + outbox_event INSERT  ──한 트랜잭션(원자적)──
   [Relay @Scheduled] 미발행 outbox → Kafka(acks=all, idempotence) → SENT
        │ catalog-events(key=productId) / order-events(key=orderId)
[commerce-streamer] @KafkaListener(manual ack)
   event_handled(event_id PK) skip → product_metrics upsert → ack
```
멱등 2종: 좋아요 수 = *스냅샷+version* 로 최신만 반영(last-write-wins), 판매량 = *누적* 이라 event_handled로 중복만 차단.

**Step 3 — 선착순 쿠폰 (Kafka 버퍼 + 순차 + 원자 차감):**
```
[사용자] POST .../issue-requests → coupon_issue_result(PENDING) + Outbox 발행 → 즉시 requestId 반환
   coupon-issue-requests (key=couponId → 쿠폰별 단일 파티션 → 순차)
[streamer] CouponIssueProcessor (한 트랜잭션, 멱등)
   event_handled(requestId)? → 유저 중복(existsBy)? → 원자 차감 → 실제 UserCoupon 발급 → result 갱신
[사용자] GET .../issue-requests/{requestId} → ISSUED / REJECTED_SOLD_OUT / REJECTED_DUPLICATE
```

### Data Models
- `outbox_event`(event_id unique, topic, message_key, event_type, payload, status) — 발행 원자성.
- `product_metrics`(product_id unique, like_count, sales_count, like_version) — 집계 투영. 좋아요는 like_version 가드로 최신만.
- `event_handled`(event_id PK) — Consumer 멱등. *로그 테이블과 분리*(정합성 제어 vs 감사, 부하·수명 상반).
- `coupon` + `quantity`(선착순 한도) + `issued_count`(원자 차감 카운터).
- `coupon_issue_result`(request_id unique, coupon_id, user_id, status, user_coupon_id, reason) — polling 대상.

### API Design
| 메서드 | 경로 | 역할 |
|---|---|---|
| POST | `/api/v1/coupons/{couponId}/issue-requests` | 선착순 발급 **접수** → `{requestId}` 즉시 반환 |
| GET | `/api/v1/coupons/issue-requests/{requestId}` | 결과 **polling** |
| POST | `/api/v1/coupons/{couponId}/issue` | (기존 동기 발급 — **무변경 유지**) |

내부 토픽: `catalog-events`(key=productId) · `order-events`(key=orderId) · `coupon-issue-requests`(key=couponId).

### 검증 — 데이터로
- **선착순 동시성(통합 테스트):** quantity=100, **서로 다른 300명 동시 요청 → 정확히 100 ISSUED, `issued_count`=100, 초과발급 0, 오류 0.** 원자적 조건부 차감(`UPDATE ... WHERE issued_count < quantity`, 0행=소진)이 순차가 아니어도 초과를 0으로 고정.
- **"이벤트=분리" 착각의 대가:** 좋아요 카운트를 커밋 후 리스너(REQUIRES_NEW)로 뺀 첫 구현 → 동시 20 좋아요 중 **10건 `TransactionRequiredException` → count 10**. 원인: afterCommit에 원래 tx 커넥션이 안 풀려 스레드당 커넥션 2개 요구 → 풀(10) 고갈. *테스트가 설계 결함을 잡아냈고*, 운영 카운트를 tx에 되돌리는 결정으로 이어짐(Alternatives A).
- **전달 보장:** 도메인+outbox 원자 저장 → 릴레이 재시도(At-Least-Once) → 소비 `event_handled(PK)` 로 "정확히 한 번 *효과*".

### Constraints
- Producer `acks=all` + `enable.idempotence=true`. Consumer `manual ack` + `event_handled(PK)` 멱등 + `version` 최신 반영.
- 파티션 키로 순서 보장(같은 productId/couponId → 같은 파티션 → 순차).
- 이벤트는 **원시값 스냅샷만**(엔티티 금지 — AFTER_COMMIT은 tx 종료 후라 LazyInit 방지).
- 기존 동기 발급/주문 흐름 **무변경**.

## Alternatives Considered

**A. 좋아요 운영 카운트 — 이벤트 vs 트랜잭션** *(이번 주 가장 값진 오답)*

| 옵션 | Pros | Cons |
|------|------|------|
| A. 카운트를 커밋 후 리스너(REQUIRES_NEW) | 과제 문구 그대로 "집계 분리" | **동시 20 중 10 유실** — 커넥션 풀 고갈 |
| **선택: B. 운영 카운트는 tx 안(원자 upsert), 이벤트는 "통지"만** | 즉시·정확·동시성 안전 | 분석 지표(product_metrics)만 최종 일관 |

**선택 근거:** "숫자가 두 개"다 — **운영 카운트**(API 즉시 정확)와 **분석 지표**(좀 늦어도 됨)는 다른 데이터. 정합성 필요한 값은 원자 연산에 남기고, 이벤트로는 *"바뀌었다는 사실"* 만 보낸다.

**B. 유저 중복 방지 — DB 유니크 vs 순차 existsBy**

| 옵션 | Pros | Cons |
|------|------|------|
| user_coupon (userId,couponId) 유니크 | 어떤 동시성에도 안전 | 기존 동기 발급 경로·테스트 파손 위험 |
| **선택: streamer `existsBy` 검사** | 기존 경로 무영향 | **파티션 순차(key=couponId) 전제** — 멀티스레드 직접 호출엔 TOCTOU 여지 |

**선택 근거:** 초과발급 0은 원자 차감이라 무조건 안전, 중복 방지는 순차에 기댄다(실운영 안전, 유니크 하드닝은 후속). 그 외: 발행=Outbox 재사용(직접 publish는 앱 죽으면 유실), 소비=manual ack(auto-commit은 처리 전 커밋→유실).

## 한계 & 다음 (솔직하게)
- **DLQ 미구현.** 공유 컨테이너 기본값이 *0ms로 ~10회 재시도 후 드롭*이라 일시 장애용 backoff(1s×5)만 추가. 무손실은 DLQ가 필요.
- **중복 방지는 순차 전제** — DB 유니크가 하드닝.
- **조회수 집계·판매량 정확 시점 미룸.** 조회수(읽기 경로)는 Outbox와 안 맞아 별도 설계; 판매량은 지금 `OrderPlaced`(생성) 기준이라 결제 실패 시 과대집계 여지 → `OrderPaid` 이벤트가 다음 과제.
- streamer가 쿠폰 도메인을 미러(공유 모듈 부재). 공유 도메인 모듈 추출이 정공법.

**한 줄 회고:** "이벤트로 빼면 느슨해지겠지"가 아니라 *"이 값이 실패하면 되돌려야 하나"* 를 먼저 물었어야 했다. 그 질문 하나가 트랜잭션·이벤트·Kafka의 경계를 다 정해줬다.

---

# Cross-cutting Concerns (필드)

- **Scalability**: Kafka 파티션·Consumer 수평 확장으로 처리량 선형 증가. Kafka가 트래픽 스파이크의 *버퍼* 역할 → 선착순 1만 요청도 API/DB가 안 무너짐. 쿠폰별 순차성은 `key=couponId` 파티셔닝으로 확장 중에도 유지.
- **Latency**: 요청 경로는 *로컬 DB insert + 이벤트 발행*만 → 즉시 응답(외부/Kafka 네트워크는 응답 경로 밖). 릴레이 폴링 주기는 "주문 지연"이 아니라 "후속 반영 지연" 다이얼.
- **Consistency**: 강한 일관성(재고·쿠폰·운영 카운트 = tx/원자연산) vs 최종 일관성(분석 집계·발급 통지 = 이벤트). 경계는 "실패 시 되돌려야 하나"로 결정.
- **Observability**: 구조화 로깅(`[UserAction]`, `[DataPlatform]`), `event_handled`(정합성 제어, 소량·핫패스)와 로그 테이블(감사, 대용량·저빈도)의 *분리* 필요성 정리. Consumer lag·DLQ 모니터링은 후속.
- **Security & Privacy**: 이번 주는 보안 표면이 작음(카드번호 미저장·금액 서버 도출은 이전 주 결정 유지).

---

# Reference (필드)

- 본인 코드(branch `volume-7`): Step1 `ApplicationEvent`(4ec69269…dd291f6f) / Step2 `Outbox+Kafka`(cf5b84d8…5056b7ec) / Step3 `선착순 쿠폰`(b905da6b…7b8f85ef). *(push 후 커밋 링크 유효)*
  - 핵심 파일: `application/outbox/OutboxEventListener`, `application/coupon/CouponIssueProcessor`, `infrastructure/coupon/CouponJpaRepository#tryConsumeQuantity`, `infrastructure/metrics/ProductMetricsJpaRepository#applyLike`.
- Spring Application Events — https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events
- Transactional Outbox Pattern — https://microservices.io/patterns/data/transactional-outbox.html
- Spring for Apache Kafka (delivery semantics / manual ack) — https://docs.spring.io/spring-kafka/reference/
