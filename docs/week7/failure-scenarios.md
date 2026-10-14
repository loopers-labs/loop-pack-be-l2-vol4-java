# 7주차 — 이벤트 파이프라인 장애 시나리오

> `analyze-external-integration` (6주차) + `design-event-driven` (7주차) Skill 적용.

## S1. 도메인 커밋 성공 + outbox INSERT 실패

| 항목 | 내용 |
|---|---|
| 트리거 | outbox 트리거 코드 버그, DB 컬럼 제약 위반 등 |
| 우리쪽 동작 | 도메인 트랜잭션 전체 롤백 — outbox INSERT 는 <b>같은 tx</b> |
| 데이터 정합성 | 도메인 변경 없음 = 이벤트 유실도 없음 — OK |
| 남는 리스크 | 없음. `@EventListener` (not `@TransactionalEventListener`) 로 원자성 확보 |

## S2. 도메인 + outbox 커밋 성공, Kafka 발행 실패

| 항목 | 내용 |
|---|---|
| 트리거 | Kafka broker 다운 / 네트워크 단절 |
| 우리쪽 동작 | `OutboxRelay` 가 예외 catch → `markFailed` → PENDING 유지 |
| 데이터 정합성 | 이벤트가 outbox 에 잔존 — 브로커 복구 시 다음 폴링에서 재전송 |
| 남는 리스크 | broker 가 오래 다운되면 outbox 무한 증가 — 후속 PR 에서 정리 스케줄러 검토 |

## S3. Kafka 발행 성공, outbox markSent 실패

| 항목 | 내용 |
|---|---|
| 트리거 | markSent 트랜잭션 커밋 직전 앱 kill |
| 우리쪽 동작 | 다음 폴링에서 같은 outbox 를 다시 발행 |
| 데이터 정합성 | Producer `enable.idempotence=true` 로 브로커 dedup — Consumer 는 event_handled 로 dedup |
| 남는 리스크 | 없음 — 재발행이 발생해도 exactly-once 유지 |

## S4. Consumer 처리 도중 앱 죽음

| 항목 | 내용 |
|---|---|
| 트리거 | product_metrics UPDATE 도중 OOM |
| 우리쪽 동작 | manual Ack 이라 오프셋 커밋 안 됨 → 다음 poll 에서 이벤트 재수신 |
| 데이터 정합성 | event_handled 에 커밋됐다면 재처리 스킵. 없다면 재처리 |
| 남는 리스크 | 부분 완료 후 죽으면 product_metrics 는 반영 + event_handled 는 미기록 → 재처리 시 중복 반영 가능. 후속 PR 에서 product_metrics 갱신과 event_handled 를 같은 tx 로 묶는 방안 검토 |

## S5. Consumer 가 payload 파싱 실패

| 항목 | 내용 |
|---|---|
| 트리거 | 스키마 변경 후 하위 호환 안 되는 이벤트 수신 |
| 우리쪽 동작 | 예외 → Ack 유보 → 무한 재시도 (poison pill) |
| 데이터 정합성 | 해당 파티션의 후속 이벤트가 blocked |
| 남는 리스크 | <b>본 PR 한계</b>. DLQ 미구축. 후속 PR 에서 도입 필요 |

## S6. 같은 이벤트가 여러 번 재수신 (재시도 폭풍)

| 항목 | 내용 |
|---|---|
| 트리거 | 브로커 rebalance / consumer 재시작 시 재수신 |
| 우리쪽 동작 | `event_handled` 조회 → 이미 처리됐으면 스킵 |
| 데이터 정합성 | 도메인 상태는 한 번만 반영 |
| 남는 리스크 | event_handled 조회가 hot path — 인덱스 필수. 이미 UNIQUE 로 걸어둠 |

## S7. Consumer 그룹 하나가 뒤처짐 (lag 폭증)

| 항목 | 내용 |
|---|---|
| 트리거 | 특정 이벤트 유형이 급증 (예: 대량 좋아요) |
| 우리쪽 동작 | 그룹별로 독립 오프셋이라 다른 그룹은 영향 없음 |
| 데이터 정합성 | 뒤처진 그룹의 집계만 지연 반영 (eventual) |
| 남는 리스크 | 사용자에게 보이는 카운트가 실시간 아님 — UX 상 허용 범위 |

## S8. 선착순 쿠폰 초과 발급

| 항목 | 내용 |
|---|---|
| 트리거 | 정원 100 인데 동시에 200 요청 도착 |
| 우리쪽 동작 | 파티션 키 = couponTemplateId 라 단일 파티션 순차 처리 → 100 발급 후 나머지 SOLD_OUT |
| 데이터 정합성 | 정확히 100건만 ISSUED — DB `template.issued_count` 도 100 |
| 남는 리스크 | 다중 인스턴스에서 한 그룹의 여러 컨슈머가 같은 파티션을 소비하는 상황은 발생 안 함 (Kafka 보장) |

## S9. 쿠폰 발급 상태 폴링에 사용자 정보 노출

| 항목 | 내용 |
|---|---|
| 트리거 | 남의 requestId 를 알아내 조회 |
| 우리쪽 동작 | <b>현재 미방어</b> — requestId 만 알면 조회 가능 |
| 데이터 정합성 | 발급 완료 여부와 userCouponId 노출 |
| 남는 리스크 | 후속 PR 에서 조회 시 X-Loopers-User-Id 헤더 검증 추가 필요 |

## 종합 — 가장 먼저 손대야 할 위험

1. **S5 (poison pill)** — DLQ 도입
2. **S4 (product_metrics + event_handled 원자성)** — 같은 tx 로 묶기
3. **S9 (쿠폰 결과 조회 인증)** — 소유자 검증 헤더 추가
