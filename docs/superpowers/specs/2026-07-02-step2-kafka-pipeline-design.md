# Step 2 — Kafka 이벤트 파이프라인 (Outbox → Kafka → product_metrics) 설계

- 작성일: 2026-07-02
- 대상 모듈: `apps/commerce-api`(Producer), `apps/commerce-streamer`(Consumer), `modules/kafka`(testFixtures)
- 범위: Round 7 Step 2. Step 1(Spring ApplicationEvent)에서 만든 이벤트를 Kafka 로 승격. 관련 [[step1]] `docs/superpowers/specs/2026-07-01-step1-application-event-boundary-design.md`.

## TL;DR

commerce-api 가 **Transactional Outbox** 로 도메인 이벤트를 At-Least-Once 발행하고, commerce-streamer 가 **멱등(event_handled) + 최신-반영(version 가드)** 으로 소비해 `product_metrics` 를 upsert 한다. Step 1 의 `LikeAdded/LikeRemoved/OrderPlaced` 이벤트를 재사용해 **동기 in-tx 리스너**가 outbox 에 기록(도메인 변경과 원자적), 별도 `@Scheduled` relay 가 Kafka 로 발행. 좋아요수는 **스냅샷(count+version) + version 가드(last-write-wins)**, 판매량은 **누적 + event_handled 중복차단**.

## 결정 요약 (brainstorming Q&A)
- **범위**: 쓰기경로 2지표(좋아요수·판매량)만 Outbox→Kafka. **조회수(ProductViewed, 읽기경로)는 미룸** — 트랜잭션이 없어 Outbox 부적합, 별도 설계 필요.
- **집계 갱신 전략**: 좋아요수 = 스냅샷+version 가드 / 판매량 = 누적+event_handled. → 과제의 `event_handled` 와 `version/updated_at 최신만` 둘 다 실제 구현.
- **Outbox 캡처**: Step 1 이벤트 재사용 + 동기 `@EventListener`(발행 tx 안) 로 outbox INSERT (원자적). Facade 직접 기록(안 2) 아님.
- **기본값**: 토픽 `catalog-events`(key=productId)/`order-events`(key=orderId); `acks=all`+`enable.idempotence=true`; relay=`@Scheduled` 폴링(즉시 깨우기 하이브리드 미적용); 소비는 기존 `BATCH_LISTENER`(배치+MANUAL ack) 재사용; 테스트는 Testcontainers Kafka.

## 배경 — 현재 인프라 (탐색 결과)
- `modules:kafka`: `KafkaTemplate` 빈 + `BATCH_LISTENER("BATCH_LISTENER_DEFAULT")` 컨테이너 팩토리(배치·MANUAL ack·동시성3). producer는 JsonSerializer, `retries:3` — **acks/idempotence 미설정**. consumer `enable-auto-commit:false`, ack-mode manual, auto.offset.reset=latest.
- commerce-api: `modules:kafka` **미의존**, kafka.yml import 없음, Outbox/KafkaTemplate 없음.
- commerce-streamer: `modules:kafka`+`modules:jpa` 의존, **같은 loopers DB**, 그러나 도메인 코드 0(demo consumer만). `spring.application.name` 오타 `commerce-api`.
- `product_metrics`/`event_handled` 테이블 없음. 마이그레이션 없음 → Hibernate `ddl-auto: create`(test/local)로 엔티티에서 생성.
- Testcontainers Kafka 픽스처 없음(MySQL 픽스처만). `modules:kafka` 가 testcontainers:kafka testFixtures 의존만 선언(소스 없음).

## 목표 / 비목표
**목표**: Outbox 로 At-Least-Once 발행 / 멱등 Consumer / version 가드 최신 반영 / product_metrics(좋아요수·판매량) 집계 / Testcontainers Kafka e2e.
**비목표**: 조회수(ProductViewed) 집계, DLQ, event_handled 정리 잡, Consumer Group 세분화, 즉시-깨우기 relay 하이브리드, OrderPaid(판매량 정확 시점) — 모두 후속.

## Producer 설계 (commerce-api)

### 스키마
- `product_like_count` + `version BIGINT` — native upsert 에서 `version = version + 1`(increase/decrease 모두). `@Modifying(clearAutomatically = true)`.
- `outbox_event`(신규): `id`(PK), `event_id`(UUID, unique), `topic`, `message_key`, `event_type`, `payload`(JSON, @Lob), `status`(PENDING/SENT), `created_at`.

### 이벤트 변경 (스냅샷)
- `domain.like.event.LikeAdded(Long userId, Long productId, long likeCount, long version, ZonedDateTime occurredAt)`; `LikeRemoved` 동일 필드. `OrderPlaced` 변경 없음.
- `LikeFacade.like/unlike`: `increase/decrease` 직후 `likeCountRepository.find(productId)` 로 count+version 읽어 이벤트에 실음.

### 컴포넌트
- `domain.outbox.{OutboxEvent, OutboxStatus, OutboxEventRepository}` + `infrastructure.outbox.OutboxEventRepositoryImpl`(`findTop200ByStatusOrderByIdAsc(PENDING)`, save).
- `application.outbox.OutboxEventListener` — `@EventListener`(동기·in-tx): `LikeAdded/LikeRemoved`→topic `catalog-events`,key=productId; `OrderPlaced`→topic `order-events`,key=orderId. payload = `ObjectMapper` JSON(eventId 포함), eventId=UUID. `outboxRepository.save(pending)`.
- `application.outbox.OutboxRelay`(`@Scheduled(fixedDelayString="${outbox.relay.interval-ms:1000}")` 스케줄러 분리): PENDING 조회 → `outboxKafkaTemplate.send(topic,key,payload).get(5s)` 확인 → `markSent`(행별). 발행은 DB tx 밖. 실패 시 PENDING 유지(재시도).
- `config.OutboxKafkaConfig`: `ProducerFactory<String,String>`(StringSerializer×2, `acks=all`, `enable.idempotence=true`; bootstrap 은 `KafkaProperties.buildProducerProperties()` 기반) + `KafkaTemplate<String,String>` **빈 이름 `outboxKafkaTemplate`** (modules:kafka 가 이미 `KafkaTemplate<Object,Object>` 를 제공하므로 relay 는 `@Qualifier("outboxKafkaTemplate")` 로 주입 — 모호성 회피) + `NewTopic`(catalog-events, order-events; partitions=3, replicas=1) → KafkaAdmin 자동 생성. 참고: modules:kafka `KafkaConfig`(@EnableKafka)는 commerce-api 컴포넌트 스캔에 함께 로드되지만 listener 가 없어 무해.
- build: `implementation(project(":modules:kafka"))`; `application.yml` 에 `kafka.yml` import.

### payload JSON 계약 (앱 간 공유 모듈 없이 필드명 규약)
- catalog: `{ eventId, type: "LikeAdded"|"LikeRemoved", productId, likeCount, version, occurredAt }`
- order: `{ eventId, type: "OrderPlaced", orderId, lines: [{ productId, quantity }], occurredAt }`

### 직렬화 주의
outbox payload 는 JSON **문자열**로 저장. relay 는 **StringSerializer** `KafkaTemplate<String,String>` 로 그대로 발행(공유 JsonSerializer 로 보내면 이중 인코딩). consumer 는 `ByteArrayDeserializer`+`ByteArrayJsonMessageConverter` → JSON 바이트를 DTO 로 파싱.

## Consumer 설계 (commerce-streamer)

### 스키마 (신규, 같은 loopers DB, ddl-auto)
- `product_metrics`: `product_id`(unique), `like_count`, `sales_count`, `like_version`(마지막 반영 좋아요 version), `updated_at`.
- `event_handled`: `event_id`(@Id PK), `handled_at`.

### 컴포넌트
- `domain.metrics.{ProductMetrics, ProductMetricsRepository}` + infra native upsert:
  - `applyLike(productId, likeCount, version)`:
    `INSERT ... VALUES(:productId,:likeCount,0,:version,NOW(),NOW()) ON DUPLICATE KEY UPDATE like_count=IF(:version>like_version,:likeCount,like_count), like_version=IF(:version>like_version,:version,like_version), updated_at=IF(:version>like_version,NOW(),updated_at)` → **version 최신만 반영**.
  - `addSales(productId, quantity)`:
    `INSERT ... VALUES(:productId,0,:quantity,0,NOW(),NOW()) ON DUPLICATE KEY UPDATE sales_count = sales_count + :quantity, updated_at=NOW()` → **누적**.
- `domain.eventhandled.{EventHandled, EventHandledRepository}`(`existsById`, save).
- `application.metrics.MetricsProcessor` — `@Transactional` per event: `existsById(eventId)`면 skip; 아니면 apply(applyLike/addSales) + `event_handled` INSERT. `DataIntegrityViolationException` 캐치 = 이미 처리(경쟁 중복).
- `interfaces.consumer.{CatalogEventConsumer, OrderEventConsumer}` — `@KafkaListener(topics=..., containerFactory=KafkaConfig.BATCH_LISTENER, groupId="product-metrics")`, `List<ConsumerRecord<String,byte[]>>` 수신 → `ObjectMapper` 로 DTO 파싱 → `MetricsProcessor` → 배치 끝 `ack.acknowledge()`. **파싱 불가 레코드는 로그+skip**(파티션 블로킹 방지; DLQ 는 후속).
- 소비 DTO: `CatalogEventMessage(eventId,type,productId,likeCount,version,occurredAt)`, `OrderEventMessage(eventId,type,orderId,lines[],occurredAt)`.
- `application.yml`: `spring.application.name` → `commerce-streamer` 정정.

## 전달 보장 · 에러 처리
- 도메인+outbox 원자적(동기 리스너). relay 발행 실패→PENDING 재시도(유실 없음). markSent 전 크래시→재발행(중복 가능) = At-Least-Once. `acks=all`+idempotence 는 세션 내 재시도 중복만 차단.
- Consumer 중복→event_handled skip(정확히 한 번 효과). 좋아요 순서/과거→version 가드 무시(최신 수렴). 처리 실패→배치 미-ack 재전달→멱등 재처리.
- poison 메시지: 로그+skip(ack)로 파티션 블로킹 방지. **DLQ 는 nice-to-have(범위 밖)**.
- event_handled 는 재전달 윈도보다 길게 보존(판매량 누적 중복 안전). 정리 잡 범위 밖.

## 테스트 전략
**단위(브로커/DB 불필요)**: OutboxEventListener(이벤트→outbox row) / OutboxRelay(PENDING→send→markSent, 실패→PENDING) / MetricsProcessor(멱등 skip / apply+기록) / **JSON 계약 라운드트립**(payload↔DTO).
**통합(Testcontainers MySQL)**: product_like_count version 증가(native) / product_metrics applyLike version 가드(older 무시)+addSales 누적 / event_handled 멱등.
**End-to-end(Testcontainers Kafka + MySQL) — 앱별 반쪽**:
- commerce-api: `like()`→outbox→relay→**catalog-events 토픽 도착**(테스트 컨슈머 확인).
- commerce-streamer: catalog-events 발행→**product_metrics 갱신**(Awaitility).
- `modules:kafka` testFixtures 에 `KafkaTestContainersConfig`(`MySqlTestContainersConfig` 패턴, `spring.kafka.bootstrap-servers` system property export) 신규 — 두 앱 공용. Awaitility 는 spring-boot-starter-test 포함.

## 구현 슬라이스 (각 = 별도 커밋, TDD)
1. `feat:` Producer 인프라 — commerce-api kafka 의존/import, `OutboxKafkaConfig`(String template+acks/idempotence+NewTopic), KafkaTestContainersConfig 픽스처.
2. `feat:` Outbox 기록 — `OutboxEvent` 엔티티/repo, `OutboxEventListener`(동기 in-tx), `product_like_count.version`+이벤트 count/version 확장, LikeFacade read-back.
3. `feat:` Outbox relay — `OutboxRelay`+스케줄러, api e2e(→catalog-events 도착).
4. `feat:` Consumer 집계 — streamer `ProductMetrics`/`EventHandled`/`MetricsProcessor`/두 Consumer, 단위+통합+e2e, app-name 정정.

## 확인됨
- 조회수/DLQ/OrderPaid/Consumer group 세분화 = 후속. 판매량은 `OrderPlaced`(생성 시점) 기준 — 결제 실패 과대집계 가능성은 [[step1]] D2 대로 Step 2 범위 밖(후속 OrderPaid).
