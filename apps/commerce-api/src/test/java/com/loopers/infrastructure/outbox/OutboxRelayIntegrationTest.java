package com.loopers.infrastructure.outbox;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.config.KafkaTopicConfig;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transactional Outbox producer 파이프 전 구간을 실제 Kafka(Testcontainer)로 못박는 통합 테스트.
 *
 * <p>좋아요 등록(트랜잭션) → BEFORE_COMMIT 리스너가 <b>같은 TX</b>에 outbox 행 INSERT → @Scheduled relay 가
 * PENDING 을 읽어 Kafka(catalog-events)로 발행 → SENT 마킹. 실제 컨슈머로 메시지를 수신해 key=productId,
 * payload(PRODUCT_LIKED + eventId) 를 검증한다. 이 한 테스트가 String 직렬화(이중 인코딩 회피)·토픽 생성·
 * 스케줄링·상태 전이를 동시에 커버한다.</p>
 */
@SpringBootTest
class OutboxRelayIntegrationTest {

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private OutboxTxHarness outboxTxHarness;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private KafkaProperties kafkaProperties;

    private Long productId;

    @BeforeEach
    void setUp() {
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
        productId = productJpaRepository.save(Product.create(brandId, "상품1", Money.of(1_000L))).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록이 커밋되면 outbox 행이 같은 TX로 남고, relay 가 catalog-events 로 발행한다.")
    @Test
    void like_writesOutbox_andRelayPublishesToKafka() {
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(KafkaTopicConfig.CATALOG_EVENTS));

            likeApplicationService.register(1L, productId);

            // BEFORE_COMMIT 리스너가 같은 TX에서 적재 → 커밋 후 행이 존재한다.
            List<OutboxEvent> rows = outboxEventJpaRepository.findAll();
            assertThat(rows).hasSize(1);
            OutboxEvent row = rows.get(0);
            assertThat(row.getTopic()).isEqualTo(KafkaTopicConfig.CATALOG_EVENTS);
            assertThat(row.getAggregateId()).isEqualTo(String.valueOf(productId));

            // 먼저 outbox 가 PUBLISHED 로 수렴하는지 = relay 가 실제로 broker ack 를 받았는지 증명한다.
            OutboxStatus status = awaitOutboxPublished(row.getId());
            assertThat(status).as("relay 가 발행 후 PUBLISHED 로 마킹해야 한다").isEqualTo(OutboxStatus.PUBLISHED);

            // 그다음 relay 가 발행한 메시지를 eventId 로 특정해 수신 검증(토픽 누적 오염 회피).
            ConsumerRecord<String, String> record = awaitRecordWithEventId(consumer, row.getEventId());
            assertThat(record.key()).isEqualTo(String.valueOf(productId));
            assertThat(record.value())
                    .contains("PRODUCT_LIKED")
                    .contains("\"productId\":" + productId);
        }
    }

    @DisplayName("발행 트랜잭션이 롤백되면 BEFORE_COMMIT 이 호출되지 않아 outbox 행이 남지 않는다(원자성).")
    @Test
    void rolledBackPublish_doesNotWriteOutbox() {
        assertThatThrownBy(() ->
                outboxTxHarness.publishThenRollback(new ProductLikedEvent(1L, productId, ZonedDateTime.now())))
                .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEventJpaRepository.count())
                .as("롤백된 발행은 outbox 에 흔적을 남기면 안 된다").isZero();
    }

    private KafkaConsumer<String, String> newConsumer() {
        Map<String, Object> props = new HashMap<>();
        // producer(relay)가 실제로 붙는 것과 동일한 bootstrap 을 KafkaProperties 에서 그대로 가져온다.
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-outbox-verify-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private ConsumerRecord<String, String> awaitRecordWithEventId(KafkaConsumer<String, String> consumer, String eventId) {
        long deadline = System.currentTimeMillis() + 20_000L;
        int seen = 0;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                seen++;
                if (record.value() != null && record.value().contains(eventId)) {
                    return record;
                }
            }
        }
        throw new AssertionError("catalog-events 에서 eventId=" + eventId + " 메시지를 수신하지 못했습니다. (폴로 읽은 레코드 수=" + seen + ")");
    }

    private OutboxStatus awaitOutboxPublished(Long id) {
        OutboxStatus status = OutboxStatus.PENDING;
        for (int i = 0; i < 100; i++) {
            status = outboxEventJpaRepository.findById(id).orElseThrow().getStatus();
            if (status == OutboxStatus.PUBLISHED) {
                return status;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return status;
    }

    @TestConfiguration
    static class OutboxTxHarnessConfig {
        @Bean
        OutboxTxHarness outboxTxHarness(ApplicationEventPublisher publisher) {
            return new OutboxTxHarness(publisher);
        }
    }

    /** 이벤트 발행을 트랜잭션 경계 안에서 통제하는 하네스(별도 빈 → 프록시 경유로 @Transactional 적용). */
    static class OutboxTxHarness {
        private final ApplicationEventPublisher publisher;

        OutboxTxHarness(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publishThenRollback(Object event) {
            publisher.publishEvent(event);
            throw new IllegalStateException("force rollback");
        }
    }
}
