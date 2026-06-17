package com.loopers.infrastructure.product;

import com.loopers.interfaces.consumer.LikeChangedMessage;
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
 * 좋아요 비동기 집계의 핵심 로직을 실제 DB(+Kafka)로 검증한다.
 * - {@link ProductLikesUpdater}: 상품별 합산 델타를 batch UPDATE로 반영(음수 가드 포함)
 * - {@link LikeCountReconciler}: product_like 활성 행 수로 likes_count 교정
 * - 컨슈머 end-to-end: 토픽으로 보낸 좋아요 이벤트가 합산되어 likes_count에 반영
 *
 * <p>commerce-streamer는 JPA 엔티티가 없으므로 테스트가 product/product_like 테이블을 직접 만든다.
 */
@SpringBootTest
class LikeCountAggregationIntegrationTest {

    @Autowired ProductLikesUpdater productLikesUpdater;
    @Autowired LikeCountReconciler likeCountReconciler;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${like-events.topic}")
    String topic;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    likes_count BIGINT NOT NULL DEFAULT 0
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_like (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    product_id BIGINT NOT NULL,
                    deleted_at DATETIME(6) NULL
                )""");
        jdbcTemplate.execute("TRUNCATE TABLE product");
        jdbcTemplate.execute("TRUNCATE TABLE product_like");
    }

    private long likesCount(long productId) {
        return jdbcTemplate.queryForObject("SELECT likes_count FROM product WHERE id = ?", Long.class, productId);
    }

    @DisplayName("applyDeltas는 상품별 합산 델타를 반영하고, 음수로 떨어지면 0으로 가드한다")
    @Test
    void applyDeltas_appliesAndGuardsNegative() {
        jdbcTemplate.update("INSERT INTO product (id, likes_count) VALUES (1, 0), (2, 5)");

        productLikesUpdater.applyDeltas(Map.of(1L, 5L, 2L, -2L));
        assertThat(likesCount(1)).isEqualTo(5);
        assertThat(likesCount(2)).isEqualTo(3);

        productLikesUpdater.applyDeltas(Map.of(2L, -100L));  // 과차감 → 0 가드
        assertThat(likesCount(2)).isEqualTo(0);
    }

    @DisplayName("reconcile은 product_like 활성 행 수로 likes_count를 교정한다(틀어진 값/soft delete 무관)")
    @Test
    void reconcile_recomputesFromSourceOfTruth() {
        jdbcTemplate.update("INSERT INTO product (id, likes_count) VALUES (1, 999), (2, 7)");
        // 상품 1: 활성 좋아요 3건 + 취소(soft delete) 1건 → 정답 3
        jdbcTemplate.update("INSERT INTO product_like (product_id, deleted_at) VALUES (1, NULL), (1, NULL), (1, NULL), (1, NOW())");
        // 상품 2: 활성 0건 → 정답 0

        likeCountReconciler.reconcile();

        assertThat(likesCount(1)).isEqualTo(3);
        assertThat(likesCount(2)).isEqualTo(0);
    }

    @DisplayName("토픽으로 보낸 좋아요 이벤트가 컨슈머에서 합산되어 likes_count에 반영된다(end-to-end)")
    @Test
    void consumer_aggregatesFromTopic() {
        jdbcTemplate.update("INSERT INTO product (id, likes_count) VALUES (1, 0)");

        for (int i = 0; i < 5; i++) {
            kafkaTemplate.send(topic, "1", new LikeChangedMessage(1L, +1));
        }

        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(likesCount(1)).isEqualTo(5));
    }
}
