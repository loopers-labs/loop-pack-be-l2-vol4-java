# 7주차 — Event vs Command 판단 기준

> "이걸 이벤트로 분리해야 하는가?" — 무조건 분리가 아니라, 아래 4개 질문을 순서대로 답해 결정한다.

## 1. 판단 프레임

### Q1. 주요 로직의 실패와 부가 로직의 실패가 **함께 실패해야 하는가?**
- **함께 실패** → 커맨드 (Command) 로 유지. 같은 트랜잭션에서 처리.
- **분리 실패** → 이벤트 (Event) 로 분리. 부가 로직이 실패해도 주요 로직은 성공.

예:
- 주문 완료 시 결제 → 커맨드 (결제 실패면 주문도 실패해야 함)
- 주문 완료 시 알림 발송 → 이벤트 (알림 실패로 주문을 롤백하지 않는다)

### Q2. 부가 로직이 **다른 도메인/시스템 소유**인가?
- **다른 도메인** → 이벤트로 분리 → 도메인 경계를 명시적으로 만든다
- **같은 도메인** → 커맨드로 유지해도 무방

예:
- 좋아요 카운트 UI 반영 (product_metrics) → 이벤트 (분석/통계 시스템의 관심사)
- 좋아요 → user_likes 테이블 저장 → 커맨드 (좋아요 도메인 내부)

### Q3. 응답 지연에 부가 로직이 **포함돼도 되는가?**
- **포함 안 됨** → 이벤트 + @Async (또는 Kafka)
- **포함 됨** → 커맨드로 동기 처리

예:
- 상품 조회 시 조회수 집계 → 이벤트 + @Async (조회수 반영을 기다리게 하면 UX 저해)
- 상품 조회 시 상품 정보 조회 → 커맨드 (본질적으로 응답에 필요)

### Q4. 트랜잭션 결과에 **결과가 좌우되는가?**
- **좌우됨** → `@TransactionalEventListener(phase = AFTER_COMMIT)` — 롤백된 도메인의 부가 로직은 실행 안 됨
- **무관** → `@EventListener` — 도메인 커밋 여부와 무관하게 처리

예:
- 결제 성공 → 사용자 활동 로깅 → AFTER_COMMIT (롤백된 결제를 로깅하면 안 됨)
- Outbox 저장 → 같은 트랜잭션 안에서 (@EventListener 인라인) — Transactional Outbox 패턴의 핵심

## 2. 본 주차 적용 결과

| 원 로직 | 분리 이벤트 | 판단 근거 | 리스너 phase |
|---|---|---|---|
| `LikeService.like/unlike` | `LikeChangedEvent` | 좋아요 집계는 다른 도메인 (metrics) 소유, 실패 격리 원함 | AFTER_COMMIT (로깅), 같은 tx (outbox 저장) |
| `ProductFacade.getProductDetail` | `ProductViewedEvent` | 조회수 집계는 응답 지연에 포함되면 안 됨 | AFTER_COMMIT + @Async (로깅), 같은 tx (outbox) |
| `PaymentFacade.applyExternalStatus` | `PaymentSettledEvent` | 결제 후 알림/로깅은 결제 자체와 분리, 다른 관심사 | AFTER_COMMIT (로깅), 같은 tx (outbox) |
| `PaymentFacade` (order paid 성공 시) | `OrderCompletedEvent` | 주문 완료는 판매량 집계의 트리거 — 다른 도메인 | AFTER_COMMIT (로깅), 같은 tx (outbox) |
| `CouponIssueRequestFacade.enqueue` | `CouponIssueRequestedEvent` | 쿠폰 발급 처리는 비동기 위임 대상 | 같은 tx (outbox) — Kafka 로 발행 후 Consumer 가 처리 |

## 3. 두 종류의 리스너

같은 도메인 이벤트를 **두 가지 리스너**가 각각 다른 phase 로 받는다:

### 3.1 Outbox 리스너 (`@EventListener`, 같은 tx)
- Transactional Outbox 패턴의 핵심 — 도메인 커밋과 outbox row INSERT 가 <b>원자적</b>이어야 함
- 커밋 후 발행하면 커밋된 도메인의 이벤트가 outbox 에 남지 못하는 창이 생김
- 실패 시 도메인 트랜잭션도 롤백 — 이벤트 유실 방지

### 3.2 로깅/부가 로직 리스너 (`@TransactionalEventListener(AFTER_COMMIT)`, `@Async`)
- 도메인 커밋 후에만 실행 — 롤백된 이벤트로 로그 남기지 않음
- @Async 로 응답 지연에 포함되지 않음
- 실패해도 도메인은 이미 커밋 완료 — 사용자 응답은 성공

## 4. 안 하는 것

| 회피 대상 | 이유 |
|---|---|
| 무조건 모든 부가 로직을 이벤트로 분리 | 트랜잭션 결합이 필요한 것까지 분리하면 정합성이 깨진다 |
| 단일 서비스 내 흐름을 이벤트로 쪼개기 | 흐름 추적이 어려워지고 디버깅 비용만 증가 |
| Kafka 로 즉시 발행 | Outbox 없이 Kafka 발행하면 도메인 커밋 성공 + Kafka 실패 시 이벤트 유실 |
