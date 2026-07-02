package com.loopers.application.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
class MetricsAggregationServiceIntegrationTest {

    @Autowired
    private MetricsAggregationService metricsAggregationService;
    @Autowired
    private ProductMetricsRepository productMetricsRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ObjectNode likePayload(long productId) {
        return objectMapper.createObjectNode().put("userId", 1L).put("productId", productId);
    }

    @DisplayName("좋아요 이벤트를 반영하면, 해당 상품의 like_count 가 1 증가한다.")
    @Test
    void increasesLikeCount_whenProductLikedEventAggregated() {
        metricsAggregationService.aggregate("evt-1", "ProductLikedEvent", likePayload(7L));

        ProductMetrics metrics = productMetricsRepository.findByProductId(7L).orElseThrow();
        assertThat(metrics.getLikeCount()).isEqualTo(1L);
    }

    @DisplayName("같은 eventId 의 이벤트가 두 번 도착해도, 집계는 한 번만 반영된다. (멱등)")
    @Test
    void aggregatesOnlyOnce_whenSameEventArrivesTwice() {
        metricsAggregationService.aggregate("evt-dup", "ProductLikedEvent", likePayload(7L));
        metricsAggregationService.aggregate("evt-dup", "ProductLikedEvent", likePayload(7L));

        ProductMetrics metrics = productMetricsRepository.findByProductId(7L).orElseThrow();
        assertThat(metrics.getLikeCount()).isEqualTo(1L);
    }

    @DisplayName("집계가 0 인 상품에 좋아요 취소 이벤트가 오면, like_count 는 0 미만으로 내려가지 않는다.")
    @Test
    void keepsLikeCountAtZero_whenUnlikeArrivesBeforeLike() {
        metricsAggregationService.aggregate("evt-unlike", "ProductUnlikedEvent", likePayload(7L));

        ProductMetrics metrics = productMetricsRepository.findByProductId(7L).orElseThrow();
        assertThat(metrics.getLikeCount()).isZero();
    }

    @DisplayName("조회 이벤트를 반영하면, view_count 가 증가한다.")
    @Test
    void increasesViewCount_whenProductViewedEventAggregated() {
        metricsAggregationService.aggregate("evt-view", "ProductViewedEvent",
            objectMapper.createObjectNode().put("productId", 7L));

        ProductMetrics metrics = productMetricsRepository.findByProductId(7L).orElseThrow();
        assertThat(metrics.getViewCount()).isEqualTo(1L);
    }

    @DisplayName("주문 생성 이벤트를 반영하면, 품목별 수량만큼 sale_count 가 증가한다.")
    @Test
    void increasesSaleCount_whenOrderCreatedEventAggregated() {
        ObjectNode payload = objectMapper.createObjectNode().put("orderId", 42L).put("userId", 1L);
        payload.putArray("items")
            .add(objectMapper.createObjectNode().put("productId", 7L).put("quantity", 2))
            .add(objectMapper.createObjectNode().put("productId", 8L).put("quantity", 1));

        metricsAggregationService.aggregate("evt-order", "OrderCreatedEvent", payload);

        assertThat(productMetricsRepository.findByProductId(7L).orElseThrow().getSaleCount()).isEqualTo(2L);
        assertThat(productMetricsRepository.findByProductId(8L).orElseThrow().getSaleCount()).isEqualTo(1L);
    }
}
