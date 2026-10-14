# 7주차 — Kafka 토픽 / 파티션 / Consumer 그룹

## 1. 토픽 설계

| 토픽 | 파티션 키 | 주요 이벤트 | Consumer Group | 담당 앱 |
|---|---|---|---|---|
| `catalog-events` | productId | LikeChanged, ProductViewed | `metrics-catalog` | commerce-streamer |
| `order-events` | orderId | OrderCompleted, PaymentSettled | `metrics-order` | commerce-streamer |
| `coupon-issue-requests` | couponTemplateId | CouponIssueRequested | `commerce-api-coupon` | commerce-api |

### 왜 이렇게 나눴나?

- **관심사 분리**: catalog / order / coupon 은 각기 다른 처리 로직 + 다른 소비자
- **파티션 키 = aggregate id**: 같은 aggregate 이벤트의 순서 보장 (like 순서, 결제 순서 등)
- **관심사별 Consumer Group**: 그룹별로 오프셋을 독립 관리 → 한 소비자가 밀려도 다른 것에 영향 없음

## 2. 파티션 키 선택 근거

파티션 키가 같으면 같은 파티션으로 라우팅되어 <b>순차 처리</b>됨. 다르면 여러 파티션에 분산되어 <b>병렬 처리</b>됨.

| 유스케이스 | 파티션 키 | 이유 |
|---|---|---|
| 좋아요 / 조회수 집계 | productId | 같은 상품의 like 증가/감소 순서 보장 → 카운트 정합 |
| 주문 완료 / 결제 확정 | orderId | 같은 주문의 상태 전이 순서 보장 |
| 선착순 쿠폰 발급 | couponTemplateId | 같은 쿠폰의 요청들을 단일 파티션에서 순차 처리 → 초과 발급 방지의 핵심 |

## 3. Producer 설정 (commerce-api)

```yaml
spring:
  kafka:
    producer:
      acks: all                                    # 리더 + ISR 전체 저장 확인
      properties:
        enable.idempotence: true                   # 재시도 중복 방지
        max.in.flight.requests.per.connection: 5   # 5 이하 필요 (idempotence 제약)
      retries: 2147483647                          # 무한 재시도 (백오프 포함)
```

### 왜 이 조합?

- `acks=all`: 리더만 저장하고 죽어도 데이터 유실 방지
- `enable.idempotence=true`: 재시도 시 producer-id + sequence-number 로 브로커가 중복 제거 → exactly-once 발행에 근접
- `max.in.flight.requests.per.connection=5`: idempotence 활성화 시 최대 5까지 허용 (5 초과 시 재시도 순서 뒤바뀔 가능성으로 실패)
- `retries=MAX`: RetriableException 은 자동 재시도 → Relay 코드가 예외 처리 부담 감소

## 4. Consumer 설정 (commerce-streamer, commerce-api-coupon)

```yaml
spring:
  kafka:
    consumer:
      group-id: commerce-streamer-metrics
      properties:
        max.poll.records: 500
    listener:
      ack-mode: manual                             # 코드에서 명시 Ack
```

### 왜 Manual Ack?

- 자동 커밋(auto ack)이면 poll 직후 오프셋이 커밋되어 처리 도중 죽으면 이벤트 유실
- Manual Ack 은 <b>처리 완료 후에만</b> `acknowledge()` 호출 → 실패 시 다음 poll 에서 재수신

### 배치 처리 결정

`KafkaConfig.BATCH_LISTENER` 팩토리는 `setBatchListener(true)` 로 배치 수신 활성:
- 배치 안 이벤트를 하나씩 handler 로 위임
- 하나가 실패하면 배치 전체 재시도 → `event_handled` 로 이미 처리된 이벤트는 스킵되고 실패한 것만 재처리

## 5. Consumer 그룹 분리 — 왜?

한 앱(commerce-streamer)이 여러 그룹으로 소비하는 이유:

| 그룹 | 담당 | 분리 이유 |
|---|---|---|
| `metrics-catalog` | catalog-events → product_metrics 갱신 | 좋아요/조회수 이벤트 밀림이 주문 처리를 늦추지 않게 |
| `metrics-order` | order-events → 판매량 갱신 | 반대로도 마찬가지 |

각 그룹은 독립 오프셋으로 lag 관리 → 한쪽이 밀려도 다른쪽 진행.

## 6. 향후 확장

| 항목 | 도입 시점 |
|---|---|
| 파티션 수 튜닝 | 처리량 병목 관측 후 |
| Consumer 배치 처리 (batch DB write) | 단건 write 로 병목 발생 시 |
| DLQ (Dead Letter Queue) | 반복 실패 이벤트 격리 필요 시 |
| Consumer Group Rebalance 옵션 | 스케일 아웃 시 downtime 최소화 위해 `sticky` 검토 |
