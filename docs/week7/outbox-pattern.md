# 7주차 — Transactional Outbox 패턴

## 1. 문제

애플리케이션이 다음 두 가지를 **동시에** 처리하고 싶을 때:
1. 도메인 상태 변경 (DB INSERT/UPDATE)
2. 외부 시스템에 이벤트 발행 (Kafka)

세 가지 실패 시나리오가 있다:

| 순서 | 실패 | 결과 |
|---|---|---|
| DB 커밋 후 Kafka 발행 | Kafka 발행 실패 | 도메인은 변경됐는데 이벤트 유실 |
| Kafka 발행 후 DB 커밋 | DB 커밋 실패 | 도메인 변경 없는데 이벤트만 나감 (환상 이벤트) |
| 동시 처리 | 부분 실패 | 정합성 깨짐 |

## 2. 해결 — Outbox

같은 DB 트랜잭션에 outbox 행을 함께 INSERT 하고, 별도 프로세스(Relay)가 outbox 를 폴링해 Kafka 로 발행.

```
┌──────────────────────────────┐
│ 도메인 트랜잭션               │
│                              │
│  UPDATE like_count           │
│  INSERT outbox (event=Like…) │
│  COMMIT                      │
└──────────────────────────────┘
        │
        │ (별도 스케줄러)
        ▼
   OutboxRelay
        │
        │ Kafka send + acks=all + idempotence
        ▼
     Broker
```

## 3. 스키마

```sql
CREATE TABLE outbox_message (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id        VARCHAR(40) NOT NULL,           -- UUID, consumer 멱등 판단
  aggregate_type  VARCHAR(40) NOT NULL,           -- Product, Order, Payment, Coupon
  aggregate_id    VARCHAR(80) NOT NULL,
  event_type      VARCHAR(60) NOT NULL,           -- LikeChanged, OrderCompleted, ...
  topic           VARCHAR(60) NOT NULL,
  partition_key   VARCHAR(80) NOT NULL,           -- Kafka 파티션 결정 키
  payload         TEXT NOT NULL,                  -- JSON
  status          VARCHAR(20) NOT NULL,           -- PENDING, SENT
  sent_at         TIMESTAMP NULL,
  retry_count     INT NOT NULL DEFAULT 0,
  last_error      VARCHAR(500) NULL,
  created_at      TIMESTAMP NOT NULL,
  updated_at      TIMESTAMP NOT NULL,
  UNIQUE KEY uk_event_id (event_id),
  KEY idx_status_created (status, created_at)
);
```

## 4. Writer — 도메인 이벤트 → outbox

`OutboxEventListener` 가 `@EventListener` (transactional listener 아님) 로 도메인 이벤트를 받아 outbox 저장:

```java
@EventListener
public void onLikeChanged(LikeChangedEvent event) {
    outboxAppender.append(
        "Product", event.productId().toString(), "LikeChanged",
        KafkaTopics.CATALOG_EVENTS, event.productId().toString(), event
    );
}
```

- `@EventListener` (not `@TransactionalEventListener`) → 도메인 트랜잭션 안에서 실행
- outbox INSERT 실패 시 도메인도 롤백 → 원자성 확보

## 5. Relay — outbox → Kafka

```java
@Scheduled(fixedDelay=1s)
public void relayPending() {
    List<OutboxMessage> batch = outboxService.loadPendingBatch(100);
    for (OutboxMessage m : batch) {
        try {
            kafkaTemplate.send(new ProducerRecord<>(m.getTopic(), m.getPartitionKey(), m.getPayload())).get(10s);
            outboxService.markSent(m.getId());          // REQUIRES_NEW
        } catch (Exception e) {
            outboxService.markFailed(m.getId(), e.getMessage());  // REQUIRES_NEW
        }
    }
}
```

- `send().get(timeout)` — 브로커 저장 확인 후에만 SENT 로 마킹
- `markSent/markFailed` 는 REQUIRES_NEW — 배치 내 개별 메시지가 서로 영향 안 주게

## 6. 재시도 & 멱등

- Producer: `acks=all` + `enable.idempotence=true` + `retries=Int.MAX` → 브로커 저장 성공 시 중복 없이 저장
- Relay: 발행 실패한 메시지는 `PENDING` 유지 → 다음 폴링에서 재시도
- Consumer: `event_handled` 테이블로 최종 멱등 보장 (같은 event_id 는 재처리 스킵)

## 7. 왜 CDC 가 아닌 Polling?

Debezium 같은 CDC 도구는 outbox 를 bin-log 로 실시간 스트림하지만, 학습 단계에선:
- 도구 도입 비용 (Debezium/Kafka Connect) 이 큼
- Polling 1초 주기면 대부분 유스케이스 지연 허용치 안
- 관측/디버깅이 쉬움 (그냥 SQL 조회)

## 8. 다중 인스턴스 운영 시 필요한 것

본 주차는 단일 인스턴스 가정. 다중 인스턴스면:
- `SELECT ... FOR UPDATE SKIP LOCKED` (MySQL 8.0+) — 여러 Relay 가 다른 배치를 잡음
- 또는 ShedLock 등 분산 락 라이브러리로 한 순간 한 Relay 만 실행

## 9. 한계 / 미적용

| 항목 | 상태 |
|---|---|
| outbox 정리 (오래된 SENT 삭제) | 미적용 — 후속 PR 에서 스케줄러 추가 |
| CDC (Debezium) | 미적용 — 학습 단계 |
| Saga / Compensating Transaction | 미적용 — 결제 롤백 시나리오 정도만 6주차에서 다룸 |
