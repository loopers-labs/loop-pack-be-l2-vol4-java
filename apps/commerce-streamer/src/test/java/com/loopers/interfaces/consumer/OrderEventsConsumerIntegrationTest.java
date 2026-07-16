package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.idempotency.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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
class OrderEventsConsumerIntegrationTest {

    private static final Acknowledgment NO_OP_ACK = () -> { };

    private final OrderEventsConsumer orderEventsConsumer;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    OrderEventsConsumerIntegrationTest(
        OrderEventsConsumer orderEventsConsumer,
        ProductMetricsJpaRepository productMetricsJpaRepository,
        EventHandledJpaRepository eventHandledJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.orderEventsConsumer = orderEventsConsumer;
        this.productMetricsJpaRepository = productMetricsJpaRepository;
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("주문 이벤트를 받으면 상품별 수량만큼 product_metrics.sales_count 가 누적된다.")
    @Test
    void increasesSalesCountPerProduct_whenOrderConsumed() {
        // given : 상품 1(수량 3), 상품 2(수량 2) 를 담은 주문 1건
        // when
        orderEventsConsumer.consume(List.of(orderRecord("evt-1", 100L, "1:3", "2:2")), NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadSalesCount(1L)).isEqualTo(3L),
            () -> assertThat(loadSalesCount(2L)).isEqualTo(2L),
            () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(1L)
        );
    }

    @DisplayName("같은 주문 이벤트를 두 번 받아도 판매량은 한 번만 반영된다 — 멱등.")
    @Test
    void appliesOnce_whenSameOrderEventConsumedTwice() {
        // given
        // when : 같은 eventId 가 재전송되어 두 번 소비된다
        orderEventsConsumer.consume(List.of(orderRecord("evt-1", 100L, "1:3")), NO_OP_ACK);
        orderEventsConsumer.consume(List.of(orderRecord("evt-1", 100L, "1:3")), NO_OP_ACK);

        // then
        assertAll(
            () -> assertThat(loadSalesCount(1L)).isEqualTo(3L),
            () -> assertThat(eventHandledJpaRepository.count()).isEqualTo(1L)
        );
    }

    private long loadSalesCount(long productId) {
        return productMetricsJpaRepository.findById(productId).orElseThrow().getSalesCount();
    }

    // productQuantities: "productId:quantity" 형식의 라인들
    private ConsumerRecord<String, byte[]> orderRecord(String eventId, long orderId, String... productQuantities) {
        String lines = java.util.Arrays.stream(productQuantities)
            .map(pq -> {
                String[] parts = pq.split(":");
                return "{\"productId\":%s,\"quantity\":%s}".formatted(parts[0], parts[1]);
            })
            .collect(java.util.stream.Collectors.joining(","));
        String json = """
            {"eventId":"%s","eventType":"ORDER_PLACED","aggregateId":%d,"data":{"orderId":%d,"lines":[%s]}}
            """.formatted(eventId, orderId, orderId, lines);
        return new ConsumerRecord<>("order-events", 0, 0L, String.valueOf(orderId), json.getBytes(StandardCharsets.UTF_8));
    }
}
