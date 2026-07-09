package com.loopers.infrastructure.metrics;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loopers.application.metrics.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * product_metrics 비동기 집계의 핵심 로직을 실제 DB(+Kafka)로 검증한다.
 * - {@link ProductMetricsUpdater}: 측정값(like/view/sales) 델타 batch UPDATE(음수 가드 포함)
 * - {@link ProductMetricsReconciler}: product_like COUNT / PAID 주문 수량으로 like/sales 교정
 * - 컨슈머 end-to-end: catalog-events envelope가 집계되어 like_count에 반영 + event_handled 멱등
 *
 * <p>commerce-streamer는 JPA 엔티티가 없으므로 테스트가 필요한 테이블을 직접 만든다.
 */
@SpringBootTest
class ProductMetricsAggregationIntegrationTest {

    private static final JsonNodeFactory J = JsonNodeFactory.instance;

    @Autowired ProductMetricsUpdater productMetricsUpdater;
    @Autowired ProductMetricsReconciler productMetricsReconciler;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${event-topics.catalog}") String catalogTopic;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_metrics (
                    product_id BIGINT PRIMARY KEY,
                    brand_id BIGINT NOT NULL DEFAULT 0,
                    price BIGINT NOT NULL DEFAULT 0,
                    deleted_at DATETIME(6) NULL,
                    like_count BIGINT NOT NULL DEFAULT 0,
                    sales_count BIGINT NOT NULL DEFAULT 0,
                    view_count BIGINT NOT NULL DEFAULT 0,
                    created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT NOW(6)
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS event_handled (
                    consumer_group VARCHAR(100) NOT NULL,
                    event_id BIGINT NOT NULL,
                    handled_at DATETIME(6) NOT NULL,
                    PRIMARY KEY (consumer_group, event_id)
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_like (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    product_id BIGINT NOT NULL,
                    deleted_at DATETIME(6) NULL
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(20) NOT NULL
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS order_item (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    order_id BIGINT NOT NULL,
                    product_id BIGINT NOT NULL,
                    quantity INT NOT NULL,
                    deleted_at DATETIME(6) NULL
                )""");
        jdbcTemplate.execute("TRUNCATE TABLE product_metrics");
        jdbcTemplate.execute("TRUNCATE TABLE event_handled");
        jdbcTemplate.execute("TRUNCATE TABLE product_like");
        jdbcTemplate.execute("TRUNCATE TABLE orders");
        jdbcTemplate.execute("TRUNCATE TABLE order_item");
    }

    private long col(String column, long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM product_metrics WHERE product_id = ?", Long.class, productId);
    }

    private EventEnvelope like(long eventId, long productId, long delta) {
        ObjectNode p = J.objectNode();
        p.put("productId", productId);
        p.put("delta", delta);
        return new EventEnvelope(eventId, "LIKE_CHANGED", "product", productId, 0L, "2026-07-01T00:00:00Z", p);
    }

    @DisplayName("updater는 측정값 델타(like/view/sales)를 반영하고, like는 음수로 떨어지면 0으로 가드한다")
    @Test
    void updater_appliesMeasurementDeltas() {
        jdbcTemplate.update("INSERT INTO product_metrics (product_id, like_count) VALUES (1, 0), (2, 5)");

        productMetricsUpdater.applyLikeDeltas(Map.of(1L, 5L, 2L, -2L));
        productMetricsUpdater.applyViewDeltas(Map.of(1L, 3L));
        productMetricsUpdater.applySalesDeltas(Map.of(1L, 4L));

        assertThat(col("like_count", 1)).isEqualTo(5);
        assertThat(col("like_count", 2)).isEqualTo(3);
        assertThat(col("view_count", 1)).isEqualTo(3);
        assertThat(col("sales_count", 1)).isEqualTo(4);

        productMetricsUpdater.applyLikeDeltas(Map.of(2L, -100L)); // 과차감 → 0 가드
        assertThat(col("like_count", 2)).isEqualTo(0);
    }

    @DisplayName("reconcile은 진실원천으로 like_count(product_like COUNT)/sales_count(PAID 주문 수량)를 교정한다")
    @Test
    void reconcile_recomputesFromSourceOfTruth() {
        jdbcTemplate.update("INSERT INTO product_metrics (product_id, like_count, sales_count) VALUES (1, 999, 888), (2, 7, 7)");
        // 상품 1: 활성 좋아요 3건 + 취소 1건 → like 3
        jdbcTemplate.update("INSERT INTO product_like (product_id, deleted_at) VALUES (1, NULL), (1, NULL), (1, NULL), (1, NOW())");
        // 주문 100=PAID(상품1 x2), 200=PENDING(상품1 x5, 미집계) → sales 2
        jdbcTemplate.update("INSERT INTO orders (id, status) VALUES (100, 'PAID'), (200, 'PENDING')");
        jdbcTemplate.update("INSERT INTO order_item (order_id, product_id, quantity) VALUES (100, 1, 2), (200, 1, 5)");

        productMetricsReconciler.reconcile();

        assertThat(col("like_count", 1)).isEqualTo(3);
        assertThat(col("sales_count", 1)).isEqualTo(2);
        // 상품 2: 좋아요/판매 진실원천 0건 → 0으로 교정
        assertThat(col("like_count", 2)).isEqualTo(0);
        assertThat(col("sales_count", 2)).isEqualTo(0);
    }

    @DisplayName("catalog-events로 보낸 LIKE_CHANGED envelope가 집계되어 like_count에 반영되고, 중복 eventId는 한 번만 반영된다")
    @Test
    void consumer_aggregatesAndDeduplicates() {
        jdbcTemplate.update("INSERT INTO product_metrics (product_id) VALUES (1)");

        // eventId 1..5 = +1씩 → like 5
        for (int i = 1; i <= 5; i++) {
            kafkaTemplate.send(catalogTopic, "1", like(i, 1L, +1));
        }
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(col("like_count", 1)).isEqualTo(5));

        // eventId 5(중복, 무시) + eventId 6(신규 +1) → like 6
        kafkaTemplate.send(catalogTopic, "1", like(5, 1L, +1));
        kafkaTemplate.send(catalogTopic, "1", like(6, 1L, +1));
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(col("like_count", 1)).isEqualTo(6));
    }
}
