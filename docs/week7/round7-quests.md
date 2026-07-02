# 📝 Round 7 Quests

- **Calendar**: 🧑‍🏭 Round.7
- **Tag**: `round7` `task` `BE_L2`

---

## 💻 Implementation Quest

> 이벤트 기반 아키텍처의 **Why → How → Scale** 을 한 주에 관통합니다.
> Spring `ApplicationEvent` 로 경계를 나누는 감각을 익히고,
> Kafka로 이벤트 파이프라인을 구축한 뒤, **선착순 쿠폰 발급**에 적용합니다.

### 🎯 Must-Have (이번 주에 무조건 가져가야 좋을 것 - 무조건 하세요)

- Event vs Command
- Application Event
- Kafka Producer / Consumer 기본 파이프라인
- Transactional Outbox Pattern
- Kafka 기반 선착순 쿠폰 발급

### Nice-To-Have (부가적으로 가져가면 좋을 것 - 시간이 허락하면 꼭 해보세요)

- Consumer Group 분리를 통한 관심사별 처리
- Consumer 배치 처리
- DLQ 구성

---

## 📋 과제 정보

### Step 1 — ApplicationEvent로 경계 나누기

- **무조건 이벤트 분리**가 아니라, 주요 로직과 부가 로직의 경계를 판단한다.
- 주문–결제 플로우에서 부가 로직(유저 행동 로깅, 알림 등)을 이벤트로 분리한다.
- 좋아요–집계 플로우에서 eventual consistency를 적용한다.
- 트랜잭션 결과와의 상관관계에 따라 적절한 리스너(`@TransactionalEventListener` phase 등)를 활용한다.
- "이걸 이벤트로 분리해야 하는가?" 에 대한 **판단 기준** 자체가 학습 포인트다.

### Step 2 — Kafka 이벤트 파이프라인

- `commerce-api` → Kafka → `commerce-collector` 구조로 확장한다.
- Step 1에서 분리한 이벤트 중, **시스템 간 전파가 필요한 것**을 Kafka로 발행한다.
- Producer는 **Transactional Outbox Pattern**으로 At Least Once 발행을 보장한다.
- Consumer는 이벤트를 수집해 집계(좋아요 수 / 판매량 / 조회 수)를 `product_metrics` 에 upsert한다.

### Step 3 — Kafka 기반 선착순 쿠폰 발급

- Step 2에서 익힌 Kafka를 **실전 시나리오**에 적용한다.
- API는 발급 요청을 Kafka에 발행만 하고, Consumer가 실제 쿠폰을 발급한다.
- 발급 수량 제한(e.g. 선착순 100명)에 대한 **동시성 제어**를 구현한다.

#### 토픽 설계 (예시)

- `catalog-events` (상품/재고/좋아요 이벤트, key=productId)
- `order-events` (주문/결제 이벤트, key=orderId)
- `coupon-issue-requests` (쿠폰 발급 요청, key=couponId)

#### Producer, Consumer 필수 처리

- **Producer**
  - acks=all, idempotence=true 설정
- **Consumer**
  - **manual Ack** 처리
  - `event_handled(event_id PK)` (DB or Redis) 기반의 멱등 처리
  - `version` 또는 `updated_at` 기준으로 최신 이벤트만 반영

> 왜 이벤트 핸들링 테이블과 로그 테이블을 분리하는 걸까? 에 대해 고민해보자

---

## ✅ Checklist

### 📄 Step 1 — ApplicationEvent

- [x] 주문–결제 플로우에서 부가 로직을 이벤트 기반으로 분리한다.
- [x] 좋아요 처리와 집계를 이벤트 기반으로 분리한다. (집계 실패와 무관하게 좋아요는 성공)
- [x] 유저 행동(조회, 클릭, 좋아요, 주문 등)에 대한 서버 레벨 로깅을 이벤트로 처리한다.
- [x] 동작의 주체를 적절하게 분리하고, 트랜잭션 간의 연관관계를 고민해 봅니다.

### 🎾 Step 2 — Kafka Producer / Consumer

- [ ] Step 1의 ApplicationEvent 중 **시스템 간 전파가 필요한 이벤트**를 Kafka로 발행한다.
- [ ] `acks=all`, `idempotence=true` 설정
- [ ] **Transactional Outbox Pattern** 구현
- [ ] PartitionKey 기반 이벤트 순서 보장
- [ ] Consumer가 Metrics 집계 처리 (product_metrics upsert)
- [ ] `event_handled` 테이블을 통한 멱등 처리 구현
- [ ] manual Ack + `version` / `updated_at` 기준 최신 이벤트만 반영

### 🎫 Step 3 — 선착순 쿠폰 발급

- [ ] 쿠폰 발급 요청 API → Kafka 발행 (비동기 처리)
- [ ] Consumer에서 선착순 수량 제한 + 중복 발급 방지 구현
- [ ] 발급 완료/실패 결과를 유저가 확인할 수 있는 구조 설계 (polling or callback)
- [ ] 동시성 테스트 — 수량 초과 발급이 발생하지 않는지 검증

---
