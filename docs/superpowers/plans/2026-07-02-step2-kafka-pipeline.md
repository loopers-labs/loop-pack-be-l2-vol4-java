# Step 2 — Kafka 이벤트 파이프라인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** commerce-api 가 Transactional Outbox 로 도메인 이벤트를 Kafka 에 At-Least-Once 발행하고, commerce-streamer 가 멱등(event_handled)+version 가드로 소비해 product_metrics(좋아요수·판매량)를 집계한다.

**Architecture:** Step 1 의 `LikeAdded/LikeRemoved/OrderPlaced` 를 동기 in-tx `@EventListener` 가 `outbox_event` 에 기록(도메인과 원자적) → `@Scheduled` relay 가 전용 `KafkaTemplate<String,String>`(acks=all·idempotence)로 발행 → streamer 의 `@KafkaListener`(BATCH_LISTENER, MANUAL ack)가 멱등 소비 → `product_metrics` upsert(좋아요=스냅샷+version 가드, 판매량=누적).

**Tech Stack:** Java 21, Spring Boot 3.4.4, Spring Kafka(`modules:kafka`), JPA, Testcontainers(MySQL+Kafka), JUnit5/Mockito/AssertJ/Awaitility.

## Global Constraints
- 설계 근거: `docs/superpowers/specs/2026-07-02-step2-kafka-pipeline-design.md`.
- 이벤트 payload/DTO 는 앱 간 **JSON 필드명 규약**으로 매칭(공유 모듈 없음). occurredAt 은 String(ISO).
- 커밋 prefix `feat:`/`test:`; **슬라이스별 별도 커밋**; 커밋 메시지에 Co-Authored-By **금지**.
- TDD Red→Green→Refactor, 3A. 원시값 스냅샷 이벤트(엔티티 금지).
- 조회수·DLQ·OrderPaid·event_handled 정리잡 = 범위 밖.
- 테스트: `./gradlew :apps:commerce-api:test --tests "<FQN>"`, `:apps:commerce-streamer:test`. Kafka 통합 테스트는 `KafkaTestContainersConfig` **명시 트리거**(모든 @SpringBootTest 가 브로커를 켜지 않도록).

---

## File Structure

**modules/kafka (testFixtures):**
- Create `modules/kafka/src/testFixtures/java/com/loopers/testcontainers/KafkaTestContainersConfig.java`

**commerce-api (Producer):**
- Modify `apps/commerce-api/build.gradle.kts` (kafka 의존 + testFixtures)
- Modify `apps/commerce-api/src/main/resources/application.yml` (kafka.yml import)
- Create `config/OutboxKafkaConfig.java`
- Create `domain/outbox/{OutboxEvent,OutboxStatus,OutboxEventRepository}.java`
- Create `infrastructure/outbox/{OutboxEventJpaRepository,OutboxEventRepositoryImpl}.java`
- Create `application/outbox/{OutboxEventListener,CatalogEventPayload,OrderEventPayload,OutboxRelay,OutboxRelayScheduler}.java`
- Modify `domain/like/ProductLikeCount.java` (+version), `infrastructure/like/ProductLikeCountJpaRepository.java` (version 증가)
- Modify `domain/like/event/LikeAdded.java`, `LikeRemoved.java` (+likeCount,+version)
- Modify `application/like/LikeFacade.java` (read-back), `application/like/LikeFacadeTest.java`

**commerce-streamer (Consumer):**
- Modify `apps/commerce-streamer/src/main/resources/application.yml` (app name)
- Modify `modules/kafka/src/main/resources/kafka.yml` (consumer value-deserializer 오타 정정)
- Create `domain/metrics/{ProductMetrics,ProductMetricsRepository}.java`
- Create `infrastructure/metrics/{ProductMetricsJpaRepository,ProductMetricsRepositoryImpl}.java`
- Create `domain/eventhandled/{EventHandled,EventHandledRepository}.java`
- Create `infrastructure/eventhandled/{EventHandledJpaRepository,EventHandledRepositoryImpl}.java`
- Create `application/metrics/{MetricsProcessor,CatalogEventMessage,OrderEventMessage}.java`
- Create `interfaces/consumer/{CatalogEventConsumer,OrderEventConsumer}.java`

---

## Task 1: Producer 인프라 + Kafka 테스트 픽스처

**Files:**
- Modify: `apps/commerce-api/build.gradle.kts`
- Modify: `apps/commerce-api/src/main/resources/application.yml`
- Create: `apps/commerce-api/src/main/java/com/loopers/config/OutboxKafkaConfig.java`
- Create: `modules/kafka/src/testFixtures/java/com/loopers/testcontainers/KafkaTestContainersConfig.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/config/OutboxKafkaConfigTest.java`

**Interfaces:**
- Produces: bean `outboxKafkaTemplate` (`KafkaTemplate<String,String>`, acks=all, idempotence); topics `catalog-events`,`order-events`; `KafkaTestContainersConfig.ensureStarted()`.

- [ ] **Step 1: build + config import**

`apps/commerce-api/build.gradle.kts` — `implementation(project(":modules:redis"))` 아래에 추가:
```kotlin
    implementation(project(":modules:kafka"))
```
그리고 test 블록에 추가:
```kotlin
    testImplementation(testFixtures(project(":modules:kafka")))
```
`apps/commerce-api/src/main/resources/application.yml` — config.import 에 `kafka.yml` 추가:
```yaml
  config:
    import:
      - jpa.yml
      - redis.yml
      - kafka.yml
      - logging.yml
      - monitoring.yml
```

- [ ] **Step 2: KafkaTestContainersConfig 픽스처 작성** (opt-in — @Configuration 아님)

`modules/kafka/src/testFixtures/java/com/loopers/testcontainers/KafkaTestContainersConfig.java`:
```java
package com.loopers.testcontainers;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 명시적으로 ensureStarted() 를 호출한 테스트에서만 Kafka 컨테이너를 띄운다.
 * (@Configuration 이 아니라 컴포넌트 스캔되지 않음 — 모든 @SpringBootTest 가 브로커를 켜지 않게)
 */
public final class KafkaTestContainersConfig {

    private static volatile boolean started = false;
    private static KafkaContainer kafkaContainer;

    private KafkaTestContainersConfig() {}

    public static synchronized void ensureStarted() {
        if (started) {
            return;
        }
        kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
        kafkaContainer.start();
        System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.getBootstrapServers());
        started = true;
    }
}
```
(테스트 클래스는 `static { KafkaTestContainersConfig.ensureStarted(); }` 로 컨텍스트 생성 전에 bootstrap-servers 를 세팅. system property 는 test 프로파일 yml 값보다 우선.)

- [ ] **Step 3: OutboxKafkaConfig 작성**

`config/OutboxKafkaConfig.java`:
```java
package com.loopers.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class OutboxKafkaConfig {

    public static final String CATALOG_EVENTS = "catalog-events";
    public static final String ORDER_EVENTS = "order-events";

    @Bean
    public ProducerFactory<String, String> outboxProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(ProducerFactory<String, String> outboxProducerFactory) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(CATALOG_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS).partitions(3).replicas(1).build();
    }
}
```

- [ ] **Step 4: 통합 테스트 작성 (발행→소비 왕복, 컨테이너)**

`apps/commerce-api/src/test/java/com/loopers/config/OutboxKafkaConfigTest.java`:
```java
package com.loopers.config;

import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxKafkaConfigTest {

    static { KafkaTestContainersConfig.ensureStarted(); }

    @Autowired private KafkaTemplate<String, String> outboxKafkaTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("outboxKafkaTemplate 로 발행한 메시지를 컨슈머가 그대로 받는다.")
    @Test
    void publishesAndConsumes() throws Exception {
        outboxKafkaTemplate.send(OutboxKafkaConfig.CATALOG_EVENTS, "1", "{\"hello\":\"world\"}").get();

        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(OutboxKafkaConfig.CATALOG_EVENTS));
            List<ConsumerRecord<String, String>> received = poll(consumer);
            assertThat(received).anyMatch(r -> r.value().contains("world"));
        }
    }

    private KafkaConsumer<String, String> newConsumer() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.kafka.bootstrap-servers"));
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verify");
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(p);
    }

    private List<ConsumerRecord<String, String>> poll(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return List.of(records.records(OutboxKafkaConfig.CATALOG_EVENTS).iterator().next());
            }
        }
        return List.of();
    }
}
```

- [ ] **Step 5: 실행**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.config.OutboxKafkaConfigTest"`
Expected: PASS (Docker 필요 — MySQL + Kafka 컨테이너 기동).

- [ ] **Step 6: 커밋**
```bash
git add apps/commerce-api/build.gradle.kts apps/commerce-api/src/main/resources/application.yml \
        apps/commerce-api/src/main/java/com/loopers/config/OutboxKafkaConfig.java \
        modules/kafka/src/testFixtures/java/com/loopers/testcontainers/KafkaTestContainersConfig.java \
        apps/commerce-api/src/test/java/com/loopers/config/OutboxKafkaConfigTest.java
git commit -m "feat: producer Kafka 인프라 — outboxKafkaTemplate(acks=all/idempotence)+토픽+테스트컨테이너 픽스처"
```

---

## Task 2: Outbox 기록 (도메인과 원자적)

**Files:**
- Modify: `domain/like/ProductLikeCount.java`, `infrastructure/like/ProductLikeCountJpaRepository.java`
- Modify: `domain/like/event/LikeAdded.java`, `LikeRemoved.java`
- Modify: `application/like/LikeFacade.java`, test `LikeFacadeTest.java`
- Create: `domain/outbox/{OutboxEvent,OutboxStatus,OutboxEventRepository}.java`, `infrastructure/outbox/{OutboxEventJpaRepository,OutboxEventRepositoryImpl}.java`
- Create: `application/outbox/{OutboxEventListener,CatalogEventPayload,OrderEventPayload}.java`
- Test: `application/outbox/OutboxEventListenerTest.java`, `domain/like/ProductLikeCountVersionTest.java`

**Interfaces:**
- Consumes: `LikeAdded`, `LikeRemoved`, `OrderPlaced` (Step 1).
- Produces: `OutboxEvent.pending(eventId,topic,messageKey,eventType,payload)`, `OutboxEventRepository.save/findPendingBatch()`.

- [ ] **Step 1: OutboxEvent 도메인**

`domain/outbox/OutboxStatus.java`:
```java
package com.loopers.domain.outbox;

public enum OutboxStatus { PENDING, SENT }
```
`domain/outbox/OutboxEvent.java`:
```java
package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "outbox_event",
    uniqueConstraints = @UniqueConstraint(name = "uk_outbox_event_id", columnNames = {"event_id"}))
public class OutboxEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private String eventId;
    @Column(nullable = false)
    private String topic;
    @Column(name = "message_key", nullable = false)
    private String messageKey;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Lob
    @Column(nullable = false)
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    protected OutboxEvent() {}

    private OutboxEvent(String eventId, String topic, String messageKey, String eventType, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxEvent pending(String eventId, String topic, String messageKey, String eventType, String payload) {
        return new OutboxEvent(eventId, topic, messageKey, eventType, payload);
    }

    public void markSent() { this.status = OutboxStatus.SENT; }

    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
}
```
`domain/outbox/OutboxEventRepository.java`:
```java
package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);
    List<OutboxEvent> findPendingBatch();
}
```

- [ ] **Step 2: Outbox infra**

`infrastructure/outbox/OutboxEventJpaRepository.java`:
```java
package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop200ByStatusOrderByIdAsc(OutboxStatus status);
}
```
`infrastructure/outbox/OutboxEventRepositoryImpl.java`:
```java
package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) { return jpaRepository.save(event); }

    @Override
    public List<OutboxEvent> findPendingBatch() {
        return jpaRepository.findTop200ByStatusOrderByIdAsc(OutboxStatus.PENDING);
    }
}
```

- [ ] **Step 3: payload records**

`application/outbox/CatalogEventPayload.java`:
```java
package com.loopers.application.outbox;

public record CatalogEventPayload(
    String eventId, String type, Long productId, long likeCount, long version, String occurredAt) {}
```
`application/outbox/OrderEventPayload.java`:
```java
package com.loopers.application.outbox;

import java.util.List;

public record OrderEventPayload(
    String eventId, String type, Long orderId, List<Line> lines, String occurredAt) {
    public record Line(Long productId, int quantity) {}
}
```

- [ ] **Step 4: OutboxEventListener 단위 테스트(실패)**

`application/outbox/OutboxEventListenerTest.java`:
```java
package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.order.event.OrderPlaced;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventListenerTest {

    private final OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboxEventListener listener = new OutboxEventListener(outboxRepository, objectMapper);

    @DisplayName("LikeAdded → catalog-events outbox row(key=productId, payload에 count/version).")
    @Test
    void writesCatalogOutboxOnLikeAdded() {
        listener.on(new LikeAdded(7L, 100L, 5L, 3L, ZonedDateTime.now()));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent row = captor.getValue();
        assertThat(row.getTopic()).isEqualTo("catalog-events");
        assertThat(row.getMessageKey()).isEqualTo("100");
        assertThat(row.getEventType()).isEqualTo("LikeAdded");
        assertThat(row.getPayload()).contains("\"likeCount\":5").contains("\"version\":3");
    }

    @DisplayName("OrderPlaced → order-events outbox row(key=orderId, payload에 lines).")
    @Test
    void writesOrderOutboxOnOrderPlaced() {
        listener.on(new OrderPlaced(55L, 7L, 2000L,
            List.of(new OrderPlaced.Line(11L, 2)), ZonedDateTime.now()));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent row = captor.getValue();
        assertThat(row.getTopic()).isEqualTo("order-events");
        assertThat(row.getMessageKey()).isEqualTo("55");
        assertThat(row.getPayload()).contains("\"productId\":11").contains("\"quantity\":2");
    }
}
```

- [ ] **Step 5: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.outbox.OutboxEventListenerTest"`
Expected: 컴파일 실패 (OutboxEventListener 없음).

- [ ] **Step 6: OutboxEventListener 구현**

`application/outbox/OutboxEventListener.java`:
```java
package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import com.loopers.domain.order.event.OrderPlaced;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private static final String CATALOG = "catalog-events";
    private static final String ORDER = "order-events";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // 동기 @EventListener — 도메인 @Transactional 안에서 실행되어 outbox INSERT 가 도메인 변경과 원자적
    @EventListener
    public void on(LikeAdded e) {
        String eventId = UUID.randomUUID().toString();
        CatalogEventPayload payload = new CatalogEventPayload(
            eventId, "LikeAdded", e.productId(), e.likeCount(), e.version(), e.occurredAt().toString());
        append(CATALOG, String.valueOf(e.productId()), "LikeAdded", eventId, payload);
    }

    @EventListener
    public void on(LikeRemoved e) {
        String eventId = UUID.randomUUID().toString();
        CatalogEventPayload payload = new CatalogEventPayload(
            eventId, "LikeRemoved", e.productId(), e.likeCount(), e.version(), e.occurredAt().toString());
        append(CATALOG, String.valueOf(e.productId()), "LikeRemoved", eventId, payload);
    }

    @EventListener
    public void on(OrderPlaced e) {
        String eventId = UUID.randomUUID().toString();
        var lines = e.lines().stream()
            .map(l -> new OrderEventPayload.Line(l.productId(), l.quantity()))
            .toList();
        OrderEventPayload payload = new OrderEventPayload(eventId, "OrderPlaced", e.orderId(), lines, e.occurredAt().toString());
        append(ORDER, String.valueOf(e.orderId()), "OrderPlaced", eventId, payload);
    }

    private void append(String topic, String key, String type, String eventId, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("outbox payload 직렬화 실패", ex); // 도메인 tx 롤백 → 안전
        }
        outboxRepository.save(OutboxEvent.pending(eventId, topic, key, type, json));
    }
}
```

- [ ] **Step 7: 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.outbox.OutboxEventListenerTest"`
Expected: PASS

- [ ] **Step 8: product_like_count 에 version 추가 (엔티티 + 이벤트 + LikeFacade read-back)**

`domain/like/ProductLikeCount.java` — `count` 필드 아래 추가:
```java
    @Column(name = "version", nullable = false)
    private long version;
```
그리고 getter 추가:
```java
    public long getVersion() { return version; }
```

`domain/like/event/LikeAdded.java` 전체 교체:
```java
package com.loopers.domain.like.event;

import java.time.ZonedDateTime;

public record LikeAdded(Long userId, Long productId, long likeCount, long version, ZonedDateTime occurredAt) {}
```
`domain/like/event/LikeRemoved.java` 전체 교체:
```java
package com.loopers.domain.like.event;

import java.time.ZonedDateTime;

public record LikeRemoved(Long userId, Long productId, long likeCount, long version, ZonedDateTime occurredAt) {}
```

`infrastructure/like/ProductLikeCountJpaRepository.java` — native 쿼리에 version 반영:
```java
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_like_count (product_id, like_count, version, created_at, updated_at)
        VALUES (:productId, 1, 1, NOW(), NOW())
        ON DUPLICATE KEY UPDATE like_count = like_count + 1, version = version + 1, updated_at = NOW()
        """, nativeQuery = true)
    void increase(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE product_like_count
        SET like_count = like_count - 1, version = version + 1, updated_at = NOW()
        WHERE product_id = :productId AND like_count > 0
        """, nativeQuery = true)
    void decrease(@Param("productId") Long productId);
```

`application/like/LikeFacade.java` — `like()` 의 이벤트 발행부 교체:
```java
        likeRepository.save(new Like(userId, productId));
        likeCountRepository.increase(productId);
        ProductLikeCount snapshot = likeCountRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 집계를 찾을 수 없습니다."));
        eventPublisher.publishEvent(new LikeAdded(
            userId, productId, snapshot.getCount(), snapshot.getVersion(), ZonedDateTime.now()));
```
`unlike()` 의 이벤트 발행부 교체:
```java
        likeRepository.deleteBy(userId, productId);
        likeCountRepository.decrease(productId);
        ProductLikeCount snapshot = likeCountRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 집계를 찾을 수 없습니다."));
        eventPublisher.publishEvent(new LikeRemoved(
            userId, productId, snapshot.getCount(), snapshot.getVersion(), ZonedDateTime.now()));
```
import 추가: `import com.loopers.domain.like.ProductLikeCount;` (LikeCountRepository 는 이미 주입되어 있음).

- [ ] **Step 9: LikeFacadeTest 갱신 (find 스텁 + 이벤트 count/version 검증)**

`application/like/LikeFacadeTest.java` — `savesIncrementsAndPublishes` 및 `deletesDecrementsAndPublishes` 에 스냅샷 스텁/검증 반영. `Liking.savesIncrementsAndPublishes` 교체:
```java
        @DisplayName("아직 좋아요하지 않았으면, Like 저장 + 카운트 증가 + LikeAdded(스냅샷) 발행.")
        @Test
        void savesIncrementsAndPublishes() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(product()));
            when(likeCountRepository.find(PRODUCT_ID))
                .thenReturn(Optional.of(new com.loopers.domain.like.ProductLikeCount(PRODUCT_ID, 5L)));

            likeFacade.like(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository).save(any(Like.class));
            verify(likeCountRepository).increase(PRODUCT_ID);
            ArgumentCaptor<LikeAdded> captor = ArgumentCaptor.forClass(LikeAdded.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().productId()).isEqualTo(PRODUCT_ID);
            assertThat(captor.getValue().likeCount()).isEqualTo(5L);
        }
```
`Unlike.deletesDecrementsAndPublishes` 교체:
```java
        @DisplayName("좋아요한 상태면, Like 삭제 + 카운트 감소 + LikeRemoved(스냅샷) 발행.")
        @Test
        void deletesDecrementsAndPublishes() {
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(true);
            when(likeCountRepository.find(PRODUCT_ID))
                .thenReturn(Optional.of(new com.loopers.domain.like.ProductLikeCount(PRODUCT_ID, 4L)));

            likeFacade.unlike(LOGIN_ID, PRODUCT_ID);

            verify(likeRepository).deleteBy(7L, PRODUCT_ID);
            verify(likeCountRepository).decrease(PRODUCT_ID);
            verify(eventPublisher).publishEvent(any(LikeRemoved.class));
        }
```
(참고: `ProductLikeCount(productId, count)` 생성자는 version=0 으로 둠 — 단위 테스트에선 count 검증만.)

- [ ] **Step 10: product_like_count version 통합 테스트**

`domain/like/ProductLikeCountVersionTest.java`:
```java
package com.loopers.domain.like;

import com.loopers.infrastructure.like.ProductLikeCountJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductLikeCountVersionTest {

    @Autowired private LikeCountRepository likeCountRepository;
    @Autowired private ProductLikeCountJpaRepository jpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("increase 두 번이면 count=2, version=2 로 함께 증가한다.")
    @Test
    void versionIncrementsWithCount() {
        likeCountRepository.increase(1L);
        likeCountRepository.increase(1L);

        ProductLikeCount plc = jpaRepository.findByProductId(1L).orElseThrow();
        assertThat(plc.getCount()).isEqualTo(2L);
        assertThat(plc.getVersion()).isEqualTo(2L);
    }
}
```

- [ ] **Step 11: 실행 (해당 테스트 + 좋아요 회귀)**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.outbox.*" --tests "com.loopers.application.like.LikeFacadeTest" --tests "com.loopers.domain.like.ProductLikeCountVersionTest" --tests "com.loopers.concurrency.LikeConcurrencyTest"`
Expected: PASS (LikeConcurrencyTest 회귀 없음 — count 는 여전히 tx 안 원자 upsert).

- [ ] **Step 12: 커밋**
```bash
git add apps/commerce-api/src/main/java/com/loopers/domain/outbox/ \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/outbox/ \
        apps/commerce-api/src/main/java/com/loopers/application/outbox/ \
        apps/commerce-api/src/main/java/com/loopers/domain/like/ \
        apps/commerce-api/src/main/java/com/loopers/infrastructure/like/ProductLikeCountJpaRepository.java \
        apps/commerce-api/src/main/java/com/loopers/application/like/LikeFacade.java \
        apps/commerce-api/src/test/java/com/loopers/application/like/LikeFacadeTest.java \
        apps/commerce-api/src/test/java/com/loopers/application/outbox/ \
        apps/commerce-api/src/test/java/com/loopers/domain/like/ProductLikeCountVersionTest.java
git commit -m "feat: Transactional Outbox 기록 — 도메인 이벤트를 in-tx 리스너로 outbox 적재 + 좋아요 스냅샷(count/version)"
```

---

## Task 3: Outbox relay (스케줄러 발행)

**Files:**
- Create: `application/outbox/{OutboxRelay,OutboxRelayScheduler}.java`
- Test: `application/outbox/OutboxRelayTest.java`, `application/outbox/OutboxRelayE2ETest.java`

**Interfaces:**
- Consumes: `OutboxEventRepository`, `outboxKafkaTemplate` (Task 1).
- Produces: `OutboxRelay.relayOnce()`.

- [ ] **Step 1: OutboxRelay 단위 테스트(실패)**

`application/outbox/OutboxRelayTest.java`:
```java
package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxRelay relay = new OutboxRelay(repository, template);

    @DisplayName("PENDING 을 발행하고 SENT 로 표시(save)한다.")
    @Test
    void publishesAndMarksSent() {
        OutboxEvent e = OutboxEvent.pending("evt-1", "catalog-events", "100", "LikeAdded", "{}");
        when(repository.findPendingBatch()).thenReturn(List.of(e));
        when(template.send(eq("catalog-events"), eq("100"), eq("{}")))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relayOnce();

        assertThat(e.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(repository).save(e);
    }

    @DisplayName("발행 실패 시 PENDING 유지(save 안 함).")
    @Test
    void keepsPendingOnFailure() {
        OutboxEvent e = OutboxEvent.pending("evt-1", "catalog-events", "100", "LikeAdded", "{}");
        when(repository.findPendingBatch()).thenReturn(List.of(e));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(template.send(any(), any(), any())).thenReturn(failed);

        relay.relayOnce();

        assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.outbox.OutboxRelayTest"`
Expected: 컴파일 실패.

- [ ] **Step 3: OutboxRelay + 스케줄러 구현**

`application/outbox/OutboxRelay.java`:
```java
package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OutboxRelay {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxRepository,
                       @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> outboxKafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.outboxKafkaTemplate = outboxKafkaTemplate;
    }

    public void relayOnce() {
        List<OutboxEvent> batch = outboxRepository.findPendingBatch();
        for (OutboxEvent e : batch) {
            try {
                outboxKafkaTemplate.send(e.getTopic(), e.getMessageKey(), e.getPayload())
                    .get(5, TimeUnit.SECONDS); // 발행 확인 후 표시
                e.markSent();
                outboxRepository.save(e);
            } catch (Exception ex) {
                log.warn("outbox relay 발행 실패 eventId={} — 다음 폴에 재시도", e.getEventId(), ex);
                break; // 순서 보존: 실패분 뒤는 다음 폴로
            }
        }
    }
}
```
`application/outbox/OutboxRelayScheduler.java`:
```java
package com.loopers.application.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class OutboxRelayScheduler {

    private final OutboxRelay outboxRelay;

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}")
    public void relay() {
        outboxRelay.relayOnce();
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.outbox.OutboxRelayTest"`
Expected: PASS

- [ ] **Step 5: e2e — like() → relay → catalog-events 도착**

`application/outbox/OutboxRelayE2ETest.java`:
```java
package com.loopers.application.outbox;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;
import com.loopers.application.like.LikeFacade;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class OutboxRelayE2ETest {

    static { KafkaTestContainersConfig.ensureStarted(); }

    @Autowired private LikeFacade likeFacade;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        userJpaRepository.save(new User("tester01", "Password1!", "홍길동", "1990-05-14", "t@e.com", Gender.M));
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        productId = productJpaRepository.save(new Product(brand.getId(), "에어맥스", "운동화", 1000L, 10)).getId();
    }

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("like() 후 relay 가 catalog-events 토픽으로 발행한다.")
    @Test
    void likePublishesToCatalogEvents() {
        likeFacade.like("tester01", productId);

        AtomicReference<String> value = new AtomicReference<>();
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of("catalog-events"));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                var records = consumer.poll(Duration.ofMillis(500));
                records.forEach(r -> value.set(r.value()));
                assertThat(value.get()).isNotNull();
            });
        }
        assertThat(value.get()).contains("\"type\":\"LikeAdded\"").contains("\"productId\":" + productId);
    }

    private KafkaConsumer<String, String> newConsumer() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.kafka.bootstrap-servers"));
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "relay-e2e-verify");
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(p);
    }
}
```

- [ ] **Step 6: 실행**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.outbox.OutboxRelayE2ETest"`
Expected: PASS

- [ ] **Step 7: 커밋**
```bash
git add apps/commerce-api/src/main/java/com/loopers/application/outbox/OutboxRelay.java \
        apps/commerce-api/src/main/java/com/loopers/application/outbox/OutboxRelayScheduler.java \
        apps/commerce-api/src/test/java/com/loopers/application/outbox/OutboxRelayTest.java \
        apps/commerce-api/src/test/java/com/loopers/application/outbox/OutboxRelayE2ETest.java
git commit -m "feat: Outbox relay — @Scheduled 폴러가 미발행 이벤트를 Kafka로 At-Least-Once 발행"
```

---

## Task 4: Consumer 집계 (commerce-streamer)

**Files:**
- Modify: `apps/commerce-streamer/src/main/resources/application.yml` (app name)
- Modify: `modules/kafka/src/main/resources/kafka.yml` (consumer value-deserializer 오타 정정)
- Create: `domain/metrics/{ProductMetrics,ProductMetricsRepository}.java`, `infrastructure/metrics/{ProductMetricsJpaRepository,ProductMetricsRepositoryImpl}.java`
- Create: `domain/eventhandled/{EventHandled,EventHandledRepository}.java`, `infrastructure/eventhandled/{EventHandledJpaRepository,EventHandledRepositoryImpl}.java`
- Create: `application/metrics/{MetricsProcessor,CatalogEventMessage,OrderEventMessage}.java`
- Create: `interfaces/consumer/{CatalogEventConsumer,OrderEventConsumer}.java`
- Test: `application/metrics/MetricsProcessorTest.java`, `infrastructure/metrics/ProductMetricsUpsertTest.java`, `interfaces/consumer/MetricsConsumerE2ETest.java`

**Interfaces:**
- Produces: `ProductMetricsRepository.applyLike(productId,likeCount,version)/addSales(productId,quantity)/find(productId)`; `EventHandledRepository.existsById(eventId)/save`; `MetricsProcessor.handleCatalog(CatalogEventMessage)/handleOrder(OrderEventMessage)`.
- Consumes JSON 계약 (Task 2 payload 필드명과 일치).

> 참고: 이 태스크의 모든 경로는 `apps/commerce-streamer/src/main/java/com/loopers/...`.

- [ ] **Step 1: kafka.yml consumer value 역직렬화 오타 정정**

`modules/kafka/src/main/resources/kafka.yml` — consumer 블록의 잘못된 키 `value-serializer` 한 줄을 `value-deserializer` 로 바꾼다. 즉 이 줄:
```yaml
    value-serializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
```
을 다음으로 교체:
```yaml
    value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
```
(BATCH_LISTENER 는 ByteArrayJsonMessageConverter 를 쓰므로 value 는 byte[] 여야 함. demo consumer 도 동일 팩토리라 무해.)

- [ ] **Step 2: streamer app name 정정**

`apps/commerce-streamer/src/main/resources/application.yml`:
```yaml
  application:
    name: commerce-streamer
```

- [ ] **Step 3: ProductMetrics + EventHandled 도메인/인프라**

`domain/metrics/ProductMetrics.java`:
```java
package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "product_metrics",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_metrics_product", columnNames = {"product_id"}))
public class ProductMetrics extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "like_count", nullable = false)
    private long likeCount;
    @Column(name = "sales_count", nullable = false)
    private long salesCount;
    @Column(name = "like_version", nullable = false)
    private long likeVersion;

    protected ProductMetrics() {}

    public Long getProductId() { return productId; }
    public long getLikeCount() { return likeCount; }
    public long getSalesCount() { return salesCount; }
    public long getLikeVersion() { return likeVersion; }
}
```
`domain/metrics/ProductMetricsRepository.java`:
```java
package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {
    void applyLike(Long productId, long likeCount, long version);
    void addSales(Long productId, int quantity);
    Optional<ProductMetrics> find(Long productId);
}
```
`infrastructure/metrics/ProductMetricsJpaRepository.java`:
```java
package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    Optional<ProductMetrics> findByProductId(Long productId);

    // 좋아요: 스냅샷 + version 가드(최신만 반영)
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics (product_id, like_count, sales_count, like_version, created_at, updated_at)
        VALUES (:productId, :likeCount, 0, :version, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
          like_count   = IF(:version > like_version, :likeCount, like_count),
          like_version = IF(:version > like_version, :version, like_version),
          updated_at   = IF(:version > like_version, NOW(), updated_at)
        """, nativeQuery = true)
    void applyLike(@Param("productId") Long productId, @Param("likeCount") long likeCount, @Param("version") long version);

    // 판매량: 누적
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics (product_id, like_count, sales_count, like_version, created_at, updated_at)
        VALUES (:productId, 0, :quantity, 0, NOW(), NOW())
        ON DUPLICATE KEY UPDATE sales_count = sales_count + :quantity, updated_at = NOW()
        """, nativeQuery = true)
    void addSales(@Param("productId") Long productId, @Param("quantity") int quantity);
}
```
`infrastructure/metrics/ProductMetricsRepositoryImpl.java`:
```java
package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public void applyLike(Long productId, long likeCount, long version) {
        jpaRepository.applyLike(productId, likeCount, version);
    }

    @Override
    public void addSales(Long productId, int quantity) {
        jpaRepository.addSales(productId, quantity);
    }

    @Override
    public Optional<ProductMetrics> find(Long productId) {
        return jpaRepository.findByProductId(productId);
    }
}
```
`domain/eventhandled/EventHandled.java`:
```java
package com.loopers.domain.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;
    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandled() {}

    public EventHandled(String eventId) {
        this.eventId = eventId;
        this.handledAt = ZonedDateTime.now();
    }

    public String getEventId() { return eventId; }
}
```
`domain/eventhandled/EventHandledRepository.java`:
```java
package com.loopers.domain.eventhandled;

public interface EventHandledRepository {
    boolean existsByEventId(String eventId);
    void save(EventHandled eventHandled);
}
```
`infrastructure/eventhandled/EventHandledJpaRepository.java`:
```java
package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandled;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, String> {}
```
`infrastructure/eventhandled/EventHandledRepositoryImpl.java`:
```java
package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository jpaRepository;

    @Override
    public boolean existsByEventId(String eventId) { return jpaRepository.existsById(eventId); }

    @Override
    public void save(EventHandled eventHandled) { jpaRepository.save(eventHandled); }
}
```

- [ ] **Step 4: 소비 DTO + MetricsProcessor 단위 테스트(실패)**

`application/metrics/CatalogEventMessage.java`:
```java
package com.loopers.application.metrics;

public record CatalogEventMessage(
    String eventId, String type, Long productId, long likeCount, long version, String occurredAt) {}
```
`application/metrics/OrderEventMessage.java`:
```java
package com.loopers.application.metrics;

import java.util.List;

public record OrderEventMessage(
    String eventId, String type, Long orderId, List<Line> lines, String occurredAt) {
    public record Line(Long productId, int quantity) {}
}
```
`application/metrics/MetricsProcessorTest.java`:
```java
package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsProcessorTest {

    private final ProductMetricsRepository metricsRepository = mock(ProductMetricsRepository.class);
    private final EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
    private final MetricsProcessor processor = new MetricsProcessor(metricsRepository, eventHandledRepository);

    @DisplayName("새 catalog 이벤트면 applyLike + event_handled 기록.")
    @Test
    void handlesNewCatalog() {
        when(eventHandledRepository.existsByEventId("evt-1")).thenReturn(false);
        processor.handleCatalog(new CatalogEventMessage("evt-1", "LikeAdded", 100L, 5L, 3L, "t"));
        verify(metricsRepository).applyLike(100L, 5L, 3L);
        verify(eventHandledRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @DisplayName("이미 처리한 이벤트면 skip.")
    @Test
    void skipsDuplicate() {
        when(eventHandledRepository.existsByEventId("evt-1")).thenReturn(true);
        processor.handleCatalog(new CatalogEventMessage("evt-1", "LikeAdded", 100L, 5L, 3L, "t"));
        verify(metricsRepository, never()).applyLike(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @DisplayName("order 이벤트면 라인별 addSales.")
    @Test
    void handlesOrderLines() {
        when(eventHandledRepository.existsByEventId("evt-2")).thenReturn(false);
        processor.handleOrder(new OrderEventMessage("evt-2", "OrderPlaced", 55L,
            List.of(new OrderEventMessage.Line(11L, 2), new OrderEventMessage.Line(12L, 1)), "t"));
        verify(metricsRepository).addSales(11L, 2);
        verify(metricsRepository).addSales(12L, 1);
    }
}
```

- [ ] **Step 5: 실패 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.application.metrics.MetricsProcessorTest"`
Expected: 컴파일 실패.

- [ ] **Step 6: MetricsProcessor 구현**

`application/metrics/MetricsProcessor.java`:
```java
package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class MetricsProcessor {

    private final ProductMetricsRepository metricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void handleCatalog(CatalogEventMessage msg) {
        if (eventHandledRepository.existsByEventId(msg.eventId())) {
            return; // 멱등
        }
        metricsRepository.applyLike(msg.productId(), msg.likeCount(), msg.version());
        record(msg.eventId());
    }

    @Transactional
    public void handleOrder(OrderEventMessage msg) {
        if (eventHandledRepository.existsByEventId(msg.eventId())) {
            return;
        }
        msg.lines().forEach(l -> metricsRepository.addSales(l.productId(), l.quantity()));
        record(msg.eventId());
    }

    private void record(String eventId) {
        try {
            eventHandledRepository.save(new EventHandled(eventId));
        } catch (DataIntegrityViolationException e) {
            // 경쟁 중복: 이미 다른 처리에서 기록됨 — 멱등 유지
        }
    }
}
```

- [ ] **Step 7: 통과 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.application.metrics.MetricsProcessorTest"`
Expected: PASS

- [ ] **Step 8: product_metrics upsert 통합 테스트 (version 가드/누적)**

`infrastructure/metrics/ProductMetricsUpsertTest.java`:
```java
package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductMetricsUpsertTest {

    @Autowired private ProductMetricsRepository repository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("applyLike 는 version 이 더 클 때만 반영한다(오래된 이벤트 무시).")
    @Test
    void applyLikeVersionGuard() {
        repository.applyLike(1L, 10L, 5L);   // 최신
        repository.applyLike(1L, 3L, 2L);    // 과거 → 무시

        ProductMetrics m = repository.find(1L).orElseThrow();
        assertThat(m.getLikeCount()).isEqualTo(10L);
        assertThat(m.getLikeVersion()).isEqualTo(5L);
    }

    @DisplayName("addSales 는 누적한다.")
    @Test
    void addSalesAccumulates() {
        repository.addSales(1L, 2);
        repository.addSales(1L, 3);

        assertThat(repository.find(1L).orElseThrow().getSalesCount()).isEqualTo(5L);
    }
}
```

- [ ] **Step 9: 통과 확인**

Run: `./gradlew :apps:commerce-streamer:test --tests "com.loopers.infrastructure.metrics.ProductMetricsUpsertTest"`
Expected: PASS

- [ ] **Step 10: Consumer 구현 (@KafkaListener)**

`interfaces/consumer/CatalogEventConsumer.java`:
```java
package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.metrics.MetricsProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private final ObjectMapper objectMapper;
    private final MetricsProcessor metricsProcessor;

    @KafkaListener(topics = "catalog-events", groupId = "product-metrics", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                CatalogEventMessage msg = objectMapper.readValue(record.value(), CatalogEventMessage.class);
                metricsProcessor.handleCatalog(msg);
            } catch (Exception e) {
                log.error("catalog-events 처리 실패(skip) offset={}", record.offset(), e); // poison skip
            }
        }
        ack.acknowledge();
    }
}
```
`interfaces/consumer/OrderEventConsumer.java`:
```java
package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsProcessor;
import com.loopers.application.metrics.OrderEventMessage;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final MetricsProcessor metricsProcessor;

    @KafkaListener(topics = "order-events", groupId = "product-metrics", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                OrderEventMessage msg = objectMapper.readValue(record.value(), OrderEventMessage.class);
                metricsProcessor.handleOrder(msg);
            } catch (Exception e) {
                log.error("order-events 처리 실패(skip) offset={}", record.offset(), e);
            }
        }
        ack.acknowledge();
    }
}
```

- [ ] **Step 11: e2e — catalog-events 발행 → product_metrics 갱신**

`interfaces/consumer/MetricsConsumerE2ETest.java`:
```java
package com.loopers.interfaces.consumer;

import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class MetricsConsumerE2ETest {

    static { KafkaTestContainersConfig.ensureStarted(); }

    @Autowired private ProductMetricsRepository metricsRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("catalog-events(LikeAdded)를 발행하면 product_metrics.like_count 가 반영된다.")
    @Test
    void likeMetricApplied() {
        String payload = "{\"eventId\":\"e1\",\"type\":\"LikeAdded\",\"productId\":100,"
            + "\"likeCount\":7,\"version\":2,\"occurredAt\":\"2026-07-02T00:00:00Z\"}";
        publish("catalog-events", "100", payload);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(metricsRepository.find(100L)).isPresent()
                .get().extracting(m -> m.getLikeCount()).isEqualTo(7L));
    }

    private void publish(String topic, String key, String value) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.kafka.bootstrap-servers"));
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(p)) {
            producer.send(new ProducerRecord<>(topic, key, value));
            producer.flush();
        }
    }
}
```

- [ ] **Step 12: 실행**

Run: `./gradlew :apps:commerce-streamer:test`
Expected: PASS (MetricsProcessorTest + ProductMetricsUpsertTest + MetricsConsumerE2ETest).

- [ ] **Step 13: 커밋**
```bash
git add apps/commerce-streamer/src/main/java/com/loopers/ \
        apps/commerce-streamer/src/main/resources/application.yml \
        modules/kafka/src/main/resources/kafka.yml \
        apps/commerce-streamer/src/test/java/com/loopers/
git commit -m "feat: streamer Consumer — 멱등(event_handled)+version 가드로 product_metrics 집계"
```

---

## Task 5: 전체 회귀 검증

- [ ] **Step 1: 두 앱 테스트 실행**

Run: `./gradlew :apps:commerce-api:test :apps:commerce-streamer:test`
Expected: BUILD SUCCESSFUL.

---

## Self-Review (플랜 작성자 체크)
- **Spec 커버리지**: 토픽/키·acks/idempotence·NewTopic=Task1; Outbox(원자적 in-tx)·좋아요 스냅샷+version=Task2; relay(At-Least-Once)=Task3; product_metrics(version 가드/누적)·event_handled 멱등·manual ack·app-name·value-deserializer 오타=Task4. KafkaTestContainersConfig=Task1. 조회수/DLQ/OrderPaid=범위 밖(명시).
- **Placeholder 스캔**: 모든 코드 스텝 실제 코드. Step4-1 의 kafka.yml 설명 한 줄은 최종 값(ByteArrayDeserializer)을 명시.
- **타입 일관성**: `OutboxEvent.pending(eventId,topic,messageKey,eventType,payload)`; payload 필드(eventId,type,productId,likeCount,version,occurredAt / orderId,lines[productId,quantity]) ↔ 소비 DTO 동일; `applyLike(Long,long,long)`/`addSales(Long,int)`; `existsByEventId(String)`; `KafkaConfig.BATCH_LISTENER` 재사용.
- **주의**: LikeConcurrencyTest 회귀 없음(카운트 tx 유지) — Task2 Step11 에서 함께 검증. Kafka 테스트는 `KafkaTestContainersConfig.ensureStarted()` 명시 트리거라 비-Kafka @SpringBootTest 는 브로커 안 켬. `outboxKafkaTemplate` 는 `@Qualifier` 로 modules:kafka 의 `KafkaTemplate<Object,Object>` 와 구분.
