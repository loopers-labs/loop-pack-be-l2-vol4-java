# Round 7 Quest Guide

## 1. 문서 목적

이 문서는 7주차 구현 과제인 이벤트 기반 아키텍처, Kafka 이벤트 파이프라인, Kafka 기반 선착순 쿠폰 발급을 AI와 개발자가 같은 기준으로 이해하고 수행할 수 있도록 정리한 요구사항 문서다.

AI는 이 문서를 기준으로 다음 작업을 수행한다.

- 이벤트와 커맨드의 차이를 구분하고, 이벤트로 분리할 대상과 분리하지 않을 대상을 판단한다.
- Spring `ApplicationEvent`로 내부 관심사를 분리한다.
- Kafka Producer/Consumer 파이프라인을 구성하고 시스템 간 이벤트 전파를 처리한다.
- Transactional Outbox Pattern으로 이벤트 발행의 신뢰성을 높인다.
- Kafka 기반 비동기 선착순 쿠폰 발급에서 수량 제한과 중복 발급을 제어한다.
- Outbox relay, Kafka consumer, 집계 처리의 운영 메트릭을 노출한다.

## 2. 전체 목표

### 2.1 Implementation Quest

> 이벤트 기반 아키텍처의 Why, How, Scale을 한 주에 관통한다.

Spring `ApplicationEvent`로 경계를 나누는 감각을 익히고, Kafka로 이벤트 파이프라인을 구축한 뒤, 선착순 쿠폰 발급에 적용한다.

| 목표 | 설명 |
| --- | --- |
| 경계 분리 | 주요 로직과 부가 로직을 구분하고, 이벤트 분리가 적절한 지점을 판단한다. |
| 내부 이벤트 | Spring `ApplicationEvent`와 `@TransactionalEventListener`로 트랜잭션 결과와 이벤트 처리 시점을 맞춘다. |
| 외부 이벤트 | `commerce-api`에서 Kafka로 이벤트를 발행하고 수집/집계 애플리케이션에서 소비한다. |
| 발행 신뢰성 | Transactional Outbox Pattern으로 DB 변경과 이벤트 발행 간의 유실을 줄인다. |
| 비동기 쿠폰 발급 | 쿠폰 발급 요청은 Kafka에 발행하고, Consumer가 실제 발급과 동시성 제어를 담당한다. |
| 모니터링 | relay/consumer/metrics update 지표를 Actuator Prometheus endpoint로 확인할 수 있게 한다. |

### 2.2 Must-Have

- Event vs Command 구분
- Application Event
- Kafka Producer / Consumer 기본 파이프라인
- Transactional Outbox Pattern
- Kafka 기반 선착순 쿠폰 발급
- 이벤트 파이프라인 모니터링 메트릭스

### 2.3 Nice-To-Have

- Consumer Group 분리를 통한 관심사별 처리
- Consumer 배치 처리
- DLQ 구성

## 3. 사전 설계 점검

### 3.1 이벤트 분리 판단 기준

이벤트 기반 처리는 무조건 분리하는 것이 아니라, 다음 기준으로 판단한다.

| 질문 | 판단 기준 |
| --- | --- |
| 핵심 유스케이스인가? | 주문 생성, 결제 상태 변경, 쿠폰 사용처럼 사용자 요청의 성공 조건이면 커맨드 흐름에 남긴다. |
| 부가 관심사인가? | 행동 로깅, 알림, 집계 갱신처럼 실패해도 원 요청을 실패시킬 필요가 없으면 이벤트 후보로 본다. |
| 트랜잭션 결과가 필요한가? | DB 커밋 이후에만 의미가 있으면 `AFTER_COMMIT` 리스너를 우선 검토한다. |
| 시스템 간 전파가 필요한가? | 다른 애플리케이션 또는 데이터 파이프라인으로 전달해야 하면 Kafka 발행 후보로 본다. |
| 재처리와 멱등 처리가 가능한가? | 이벤트가 중복 소비되어도 결과가 깨지지 않도록 이벤트 ID와 처리 이력을 설계한다. |

### 3.2 핵심 점검 질문

구현 전에 다음 질문에 대한 답을 정리한다.

1. 이 동작은 커맨드인가, 이벤트인가?
2. 이벤트 처리 실패가 원 요청 성공 여부에 영향을 주어야 하는가?
3. 이벤트 리스너는 트랜잭션 전, 커밋 후, 롤백 후 중 어느 시점에 실행되어야 하는가?
4. Kafka로 발행할 이벤트는 내부 ApplicationEvent 중 어떤 범위인가?
5. Outbox 저장과 도메인 상태 변경은 같은 DB 트랜잭션에 포함되는가?
6. Producer 장애, Kafka 장애, Consumer 장애 시 이벤트 유실과 중복 처리를 어떻게 다룰 것인가?
7. Consumer 멱등성 기준은 `event_id`, aggregate ID, `version` 또는 `updated_at` 중 무엇인가?
8. 선착순 쿠폰 발급 결과를 사용자가 어떤 방식으로 확인할 것인가?
9. 쿠폰 수량 제한과 사용자별 중복 발급 방지는 DB 제약, row lock, 원자적 update 중 어떤 방식으로 보장할 것인가?

## 4. 구현 과제

### 4.1 Step 1 - ApplicationEvent로 경계 나누기

주요 로직과 부가 로직의 경계를 판단하고, 내부 관심사를 Spring Application Event로 분리한다.

| 대상 | 구현 기준 |
| --- | --- |
| 주문-결제 플로우 | 유저 행동 로깅, 알림 등 주문/결제 성공 조건이 아닌 부가 로직을 이벤트로 분리한다. |
| 좋아요-집계 플로우 | 좋아요 등록/취소는 즉시 처리하고, 집계 갱신은 eventual consistency를 적용한다. |
| 서버 레벨 로깅 | 조회, 클릭, 좋아요, 주문 등 사용자 행동 로그를 이벤트로 처리한다. |
| 트랜잭션 연관성 | 트랜잭션 결과와 이벤트 처리 시점이 맞도록 `@TransactionalEventListener` phase를 선택한다. |

ApplicationEvent 단계의 학습 포인트는 "이걸 이벤트로 분리해야 하는가?"에 대한 판단 기준을 세우는 것이다.

### 4.2 Step 2 - Kafka 이벤트 파이프라인

내부 이벤트 중 시스템 간 전파가 필요한 이벤트를 Kafka로 발행하고, Consumer가 수집/집계 처리를 수행한다.

| 항목 | 구현 기준 |
| --- | --- |
| 목표 구조 | `commerce-api` -> Kafka -> 수집/집계 애플리케이션 구조로 확장한다. 현재 저장소 기준 수집/집계 앱 후보는 `commerce-streamer`다. |
| 발행 대상 | Step 1에서 분리한 ApplicationEvent 중 외부 전파가 필요한 이벤트만 Kafka로 발행한다. |
| 발행 보장 | Producer는 Transactional Outbox Pattern으로 At Least Once 발행을 보장한다. |
| 집계 저장 | Consumer는 좋아요 수, 판매량, 조회 수 등을 수집해 `product_metrics`에 upsert한다. |
| 순서 보장 | aggregate 단위 순서가 필요한 이벤트는 partition key를 명확히 지정한다. |

### 4.3 Step 3 - Kafka 기반 선착순 쿠폰 발급

Kafka를 실전 시나리오에 적용해 비동기 선착순 쿠폰 발급을 구현한다.

| 항목 | 구현 기준 |
| --- | --- |
| API 책임 | 쿠폰 발급 요청 API는 요청을 검증하고 Kafka에 발행한다. 실제 발급 성공을 즉시 보장하지 않는다. |
| Consumer 책임 | Consumer가 발급 수량 제한과 사용자별 중복 발급 방지를 검증하고 실제 쿠폰을 발급한다. |
| 동시성 제어 | 선착순 수량 제한을 초과해 발급되지 않도록 DB 제약, row lock, 원자적 update 중 선택한 전략을 명확히 적용한다. |
| 결과 확인 | 발급 완료/실패 결과를 사용자가 확인할 수 있도록 polling 또는 callback 구조를 설계한다. |
| 검증 | 동시 요청에서 수량 초과 발급과 중복 발급이 발생하지 않는지 테스트한다. |

#### 현재 구현 결정

| 항목 | 결정 |
| --- | --- |
| API 응답 | `POST /api/v1/coupons/{couponId}/issue`는 실제 발급 쿠폰이 아니라 `CouponIssueRequest(PENDING)`를 반환한다. |
| 발행 방식 | API 트랜잭션에서 `CouponIssueRequest`와 `coupon-issue-requests` topic 대상 `EventOutbox`를 함께 저장한다. Kafka 전송은 기존 `EventRelayWorker`가 담당한다. |
| 결과 조회 | `GET /api/v1/coupons/issues/{requestId}` polling endpoint로 `PENDING`/`SUCCEEDED`/`FAILED` 상태를 조회한다. |
| 수량 제한 | `CouponTemplate.totalIssueLimit`를 optional 전체 발급 한도로 추가했다. null이면 기존 무제한 정책을 유지한다. |
| 동시성 제어 | consumer의 실제 발급 트랜잭션에서 쿠폰 템플릿 row를 `PESSIMISTIC_WRITE`로 잠근 뒤 전체 발급 수와 사용자별 발급 수를 확인한다. |
| 멱등 처리 | `commerce-api` consumer도 `event_handled(event_id PK)`를 사용해 중복 이벤트를 skip한다. |
| 운영 설정 | `CouponIssueRequestConsumer`는 `commerce.consumers.coupon-issue.enabled=true`일 때 활성화한다. |

## 5. 이벤트 및 토픽 설계 기준

### 5.1 이벤트 설계 원칙

- 이벤트는 이미 발생한 사실을 과거형 의미로 표현한다.
- 커맨드는 처리 요청이고, 이벤트는 처리 결과 또는 발생 사실이다.
- 이벤트 payload는 Consumer가 필요한 최소 정보를 포함하되, 내부 JPA Entity를 직접 노출하지 않는다.
- 이벤트에는 멱등 처리를 위한 `eventId`와 정렬/최신성 판단을 위한 `version` 또는 `updatedAt`을 포함한다.
- 이벤트 재처리가 가능하도록 Consumer 부작용은 멱등하게 설계한다.

### 5.2 토픽 설계 예시

| Topic | 주요 이벤트 | Partition Key |
| --- | --- | --- |
| `catalog-events` | 상품, 재고, 좋아요 이벤트 | `productId` |
| `order-events` | 주문, 결제 이벤트 | `orderId` |
| `coupon-issue-requests` | 쿠폰 발급 요청 | `couponId` |

토픽 이름과 key는 예시다. 실제 구현 시에는 현재 모듈 경계와 Consumer 책임을 기준으로 확정한다.

## 6. Producer / Consumer 처리 기준

### 6.1 Producer 기준

| 항목 | 구현 기준 |
| --- | --- |
| Kafka 설정 | `acks=all`, `enable.idempotence=true`를 사용한다. |
| 발행 방식 | 도메인 상태 변경 트랜잭션에서 Outbox를 저장하고, 별도 relay가 Kafka로 발행한다. |
| 전달 보장 | At Least Once를 기준으로 하며, Consumer 멱등 처리로 중복 발행 가능성을 흡수한다. |
| 장애 처리 | Kafka 발행 실패 시 Outbox 상태와 재시도 횟수를 기록하고 재발행 가능하게 둔다. |

### 6.2 Consumer 기준

| 항목 | 구현 기준 |
| --- | --- |
| Ack 방식 | 처리가 성공한 뒤 manual Ack를 수행한다. |
| 멱등 처리 | `event_handled(event_id PK)` 같은 처리 이력 저장소로 중복 소비를 방지한다. |
| 최신성 판단 | `version` 또는 `updated_at` 기준으로 오래된 이벤트가 최신 집계를 덮어쓰지 않게 한다. |
| 집계 반영 | `product_metrics`에 upsert하고, 실패 시 Ack하지 않아 재처리 가능하게 둔다. |
| 로그 분리 | 이벤트 처리 이력 테이블과 분석용 로그 테이블의 목적을 분리한다. |

`event_handled`는 Consumer의 멱등 처리와 재처리 안전성을 위한 운영 데이터다. 분석용 로그 테이블은 사용자 행동 분석과 조회 목적의 데이터이므로 같은 테이블로 섞지 않는다.

### 6.3 모니터링 메트릭 기준

Actuator와 Micrometer를 활용해 이벤트 파이프라인의 처리 상태를 Prometheus로 노출한다.

| 지표 | 의미 |
| --- | --- |
| `loopers.outbox.relay.success.count` | Outbox relay Kafka 발행 성공 수 |
| `loopers.outbox.relay.failure.count` | Outbox relay Kafka 발행 실패 수 |
| `loopers.outbox.relay.duration` | Outbox relay 발행 소요 시간 |
| `loopers.kafka.consumer.success.count` | Kafka consumer 처리 성공 수 |
| `loopers.kafka.consumer.failure.count` | Kafka consumer 처리 실패 수 |
| `loopers.kafka.consumer.duplicate.count` | Kafka consumer 중복 이벤트 skip 수 |
| `loopers.product_metrics.update.count` | `product_metrics` 집계 갱신 수 |

공통 tag는 `application`, `topic`, `eventType`, `result`를 우선 사용한다. `application` tag는 기존 `monitoring.yml`의 공통 설정을 따른다.

## 7. 분석 및 기록 기준

구현 결과와 함께 다음 내용을 기록한다.

- 이벤트와 커맨드를 구분한 기준
- ApplicationEvent로 분리한 대상과 분리하지 않은 대상
- `@TransactionalEventListener` phase 선택 이유
- Kafka로 발행하는 이벤트와 내부 이벤트로만 남기는 이벤트의 차이
- Outbox 저장, relay, Kafka 발행, Consumer 처리 흐름
- Producer 설정과 재시도/실패 처리 방식
- Consumer manual Ack와 멱등 처리 방식
- `product_metrics` 집계 기준과 최신 이벤트 판단 기준
- Outbox relay, Kafka consumer, product metrics update 메트릭 이름과 태그
- 선착순 쿠폰 발급의 수량 제한, 중복 발급 방지, 결과 확인 방식
- DLQ 또는 배치 Consumer를 적용했다면 적용 범위와 trade-off

## 8. 구현 체크리스트

### 8.1 ApplicationEvent

- [ ] 주문-결제 플로우에서 부가 로직을 이벤트 기반으로 분리했다.
- [x] 좋아요 처리와 집계를 이벤트 기반으로 분리했다.
- [x] 집계 실패와 무관하게 좋아요 등록/취소는 성공할 수 있다.
- [ ] 조회, 클릭, 좋아요, 주문 등 유저 행동의 서버 레벨 로깅을 이벤트로 처리했다.
- [x] 이벤트 리스너의 트랜잭션 phase를 의도에 맞게 선택했다.
- [x] 이벤트 분리 대상과 제외 대상을 문서화했다.

### 8.2 Kafka Producer / Consumer

- [x] 시스템 간 전파가 필요한 ApplicationEvent를 Kafka로 발행했다.
- [x] Producer에 `acks=all`, `enable.idempotence=true`를 설정했다.
- [x] Transactional Outbox Pattern을 구현했다.
- [x] Partition Key 기반 이벤트 순서 보장 기준을 정했다.
- [x] Consumer가 `product_metrics` 집계를 upsert한다.
- [x] `event_handled(event_id PK)` 기반 멱등 처리를 구현했다.
- [x] manual Ack를 적용했다.
- [x] `version` 또는 `updated_at` 기준으로 최신 이벤트만 반영한다.

### 8.3 모니터링 메트릭

- [x] Outbox relay 성공/실패 count와 duration 지표를 추가했다.
- [x] Kafka consumer 성공/실패/중복 처리 count 지표를 추가했다.
- [x] `product_metrics` update count 지표를 추가했다.
- [x] `/actuator/prometheus`에서 이벤트 파이프라인 지표를 확인할 수 있다.

### 8.4 Kafka 기반 선착순 쿠폰 발급

- [x] 쿠폰 발급 요청 API가 Kafka에 발급 요청을 발행한다.
- [x] Consumer가 실제 쿠폰 발급을 처리한다.
- [x] 선착순 수량 제한 초과 발급을 방지한다.
- [x] 사용자별 중복 발급을 방지한다.
- [x] 발급 완료/실패 결과를 사용자가 확인할 수 있는 구조를 설계했다.
- [x] 동시성 테스트로 수량 초과 발급이 발생하지 않음을 검증했다.

### 8.5 Nice-To-Have

- [x] Consumer Group을 관심사별로 분리했다.
- [x] Consumer 배치 처리를 적용했다.
- [ ] DLQ를 구성했다.
