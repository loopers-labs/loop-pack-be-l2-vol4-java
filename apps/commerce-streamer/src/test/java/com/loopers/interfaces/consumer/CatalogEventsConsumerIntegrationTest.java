package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.idempotency.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CatalogEventsConsumerIntegrationTest {

    private static final Acknowledgment NO_OP_ACK = () -> { };

    private final CatalogEventsConsumer catalogEventsConsumer;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CatalogEventsConsumerIntegrationTest(
        CatalogEventsConsumer catalogEventsConsumer,
        ProductMetricsJpaRepository productMetricsJpaRepository,
        EventHandledJpaRepository eventHandledJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.catalogEventsConsumer = catalogEventsConsumer;
        this.productMetricsJpaRepository = productMetricsJpaRepository;
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("LIKED 이벤트를 받으면 product_metrics.like_count 가 1 증가하고 event_handled 에 1건 기록된다.")
    @Test
    void incrementsLikeCount_whenLikedConsumed() {
        // given
        long productId = 1L;

        // when
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadLikeCount(productId)).isEqualTo(1L),
            () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(1L)
        );
    }

    @DisplayName("UNLIKED 이벤트를 받으면 like_count 가 1 감소한다.")
    @Test
    void decrementsLikeCount_whenUnlikedConsumed() {
        // given
        long productId = 1L;
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);
        catalogEventsConsumer.consume(List.of(record("evt-2", "LIKED", productId)), NO_OP_ACK);

        // when
        catalogEventsConsumer.consume(List.of(record("evt-3", "UNLIKED", productId)), NO_OP_ACK);

        // then
        assertThat(loadLikeCount(productId)).isEqualTo(1L);
    }

    @DisplayName("같은 event_id 를 두 번 받아도 like_count 는 한 번만 반영된다 — 멱등(At Least Once 흡수).")
    @Test
    void appliesOnce_whenSameEventConsumedTwice() {
        // given
        long productId = 1L;

        // when : 같은 이벤트가 재전송되어 두 번 소비된다
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);
        catalogEventsConsumer.consume(List.of(record("evt-1", "LIKED", productId)), NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadLikeCount(productId)).isEqualTo(1L),
            () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(1L)
        );
    }

    @DisplayName("한 배치 안의 서로 다른 이벤트들이 모두 반영된다.")
    @Test
    void appliesAllEventsInBatch() {
        // given
        long productId = 1L;
        List<ConsumerRecord<String, byte[]>> batch = List.of(
            record("evt-1", "LIKED", productId),
            record("evt-2", "LIKED", productId),
            record("evt-3", "LIKED", productId)
        );

        // when
        catalogEventsConsumer.consume(batch, NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadLikeCount(productId)).isEqualTo(3L),
            () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(3L)
        );
    }

    private long loadLikeCount(long productId) {
        return productMetricsJpaRepository.findById(productId).orElseThrow().getLikeCount();
    }

    private ConsumerRecord<String, byte[]> record(String eventId, String eventType, long productId) {
        String json = """
            {"eventId":"%s","eventType":"%s","aggregateId":%d,"data":{"productId":%d,"type":"%s"}}
            """.formatted(eventId, eventType, productId, productId, eventType);
        return new ConsumerRecord<>("catalog-events", 0, 0L, String.valueOf(productId), json.getBytes(StandardCharsets.UTF_8));
    }
}
