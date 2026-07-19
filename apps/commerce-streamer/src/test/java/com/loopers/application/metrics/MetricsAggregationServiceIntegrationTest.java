package com.loopers.application.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.infrastructure.ranking.RankingKeys;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class MetricsAggregationServiceIntegrationTest {

    @Autowired
    private MetricsAggregationService metricsAggregationService;
    @Autowired
    private ProductMetricsRepository productMetricsRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Double rankingScore(long productId) {
        return redisTemplate.opsForZSet().score(RankingKeys.today(), String.valueOf(productId));
    }

    private ObjectNode likePayload(long productId) {
        return objectMapper.createObjectNode().put("userId", 1L).put("productId", productId);
    }

    @DisplayName("좋아요 이벤트를 반영하면, 해당 상품의 like_count 가 1 증가한다.")
    @Test
    void increasesLikeCount_whenProductLikedEventAggregated() {
        metricsAggregationService.aggregate("evt-1", "ProductLikedEvent", likePayload(7L));

        ProductMetrics metrics = productMetricsRepository.findByProductIdAndMetricDate(7L, LocalDate.now()).orElseThrow();
        assertThat(metrics.getLikeCount()).isEqualTo(1L);
    }

    @DisplayName("같은 eventId 의 이벤트가 두 번 도착해도, 집계는 한 번만 반영된다. (멱등)")
    @Test
    void aggregatesOnlyOnce_whenSameEventArrivesTwice() {
        metricsAggregationService.aggregate("evt-dup", "ProductLikedEvent", likePayload(7L));
        metricsAggregationService.aggregate("evt-dup", "ProductLikedEvent", likePayload(7L));

        ProductMetrics metrics = productMetricsRepository.findByProductIdAndMetricDate(7L, LocalDate.now()).orElseThrow();
        assertThat(metrics.getLikeCount()).isEqualTo(1L);
    }

    @DisplayName("집계가 0 인 상품에 좋아요 취소 이벤트가 오면, like_count 는 0 미만으로 내려가지 않는다.")
    @Test
    void keepsLikeCountAtZero_whenUnlikeArrivesBeforeLike() {
        metricsAggregationService.aggregate("evt-unlike", "ProductUnlikedEvent", likePayload(7L));

        ProductMetrics metrics = productMetricsRepository.findByProductIdAndMetricDate(7L, LocalDate.now()).orElseThrow();
        assertThat(metrics.getLikeCount()).isZero();
    }

    @DisplayName("조회 이벤트를 반영하면, view_count 가 증가한다.")
    @Test
    void increasesViewCount_whenProductViewedEventAggregated() {
        metricsAggregationService.aggregate("evt-view", "ProductViewedEvent",
            objectMapper.createObjectNode().put("productId", 7L));

        ProductMetrics metrics = productMetricsRepository.findByProductIdAndMetricDate(7L, LocalDate.now()).orElseThrow();
        assertThat(metrics.getViewCount()).isEqualTo(1L);
    }

    @DisplayName("주문 생성 이벤트를 반영하면, 품목별 수량만큼 sale_count 가 증가한다.")
    @Test
    void increasesSaleCount_whenOrderCreatedEventAggregated() {
        ObjectNode payload = objectMapper.createObjectNode().put("orderId", 42L).put("userId", 1L);
        payload.putArray("items")
            .add(objectMapper.createObjectNode().put("productId", 7L).put("quantity", 2)
                .set("unitPrice", objectMapper.createObjectNode().put("amount", 1000)))
            .add(objectMapper.createObjectNode().put("productId", 8L).put("quantity", 1)
                .set("unitPrice", objectMapper.createObjectNode().put("amount", 2000)));

        metricsAggregationService.aggregate("evt-order", "OrderCreatedEvent", payload);

        assertThat(productMetricsRepository.findByProductIdAndMetricDate(7L, LocalDate.now()).orElseThrow().getSaleCount()).isEqualTo(2L);
        assertThat(productMetricsRepository.findByProductIdAndMetricDate(8L, LocalDate.now()).orElseThrow().getSaleCount()).isEqualTo(1L);
    }

    @DisplayName("좋아요 이벤트를 반영하면, 오늘 랭킹 점수가 0.2 증가한다.")
    @Test
    void increasesRankingScore_whenProductLikedEventAggregated() {
        metricsAggregationService.aggregate("evt-rank-like", "ProductLikedEvent", likePayload(7L));

        assertThat(rankingScore(7L)).isEqualTo(0.2);
    }

    @DisplayName("좋아요 취소 이벤트를 반영하면, 오늘 랭킹 점수가 0.2 감소한다.")
    @Test
    void decreasesRankingScore_whenProductUnlikedEventAggregated() {
        metricsAggregationService.aggregate("evt-rank-like", "ProductLikedEvent", likePayload(7L));
        metricsAggregationService.aggregate("evt-rank-unlike", "ProductUnlikedEvent", likePayload(7L));

        assertThat(rankingScore(7L)).isEqualTo(0.0);
    }

    @DisplayName("조회 이벤트를 반영하면, 오늘 랭킹 점수가 0.1 증가한다.")
    @Test
    void increasesRankingScore_whenProductViewedEventAggregated() {
        metricsAggregationService.aggregate("evt-rank-view", "ProductViewedEvent",
            objectMapper.createObjectNode().put("productId", 7L));

        assertThat(rankingScore(7L)).isEqualTo(0.1);
    }

    @DisplayName("주문 생성 이벤트를 반영하면, 품목별로 가중치와 로그 정규화가 적용된 랭킹 점수가 반영된다.")
    @Test
    void increasesRankingScore_whenOrderCreatedEventAggregated() {
        // 999 * 1 + 1 = 1000 → log10(1000) = 3 → 0.7 * 3 = 2.1 (깔끔하게 떨어지는 값으로 검증)
        ObjectNode payload = objectMapper.createObjectNode().put("orderId", 42L).put("userId", 1L);
        payload.putArray("items")
            .add(objectMapper.createObjectNode().put("productId", 7L).put("quantity", 1)
                .set("unitPrice", objectMapper.createObjectNode().put("amount", 999)));

        metricsAggregationService.aggregate("evt-rank-order", "OrderCreatedEvent", payload);

        assertThat(rankingScore(7L)).isCloseTo(2.1, within(0.0001));
    }
}
