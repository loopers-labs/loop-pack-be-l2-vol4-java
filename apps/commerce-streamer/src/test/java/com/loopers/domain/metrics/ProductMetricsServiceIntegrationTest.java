package com.loopers.domain.metrics;

import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
class ProductMetricsServiceIntegrationTest {

    @Autowired
    private ProductMetricsService productMetricsService;

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 이벤트를 누적 반영한다 (등록 +1, 취소 -1).")
    @Test
    void aggregatesLike() {
        productMetricsService.applyLike("evt-1", 100L, 1L);
        productMetricsService.applyLike("evt-2", 100L, 1L);
        productMetricsService.applyLike("evt-3", 100L, -1L);

        assertThat(productMetricsService_likeCount(100L)).isEqualTo(1L);
    }

    @DisplayName("같은 eventId 는 두 번 소비돼도 한 번만 반영된다 (멱등).")
    @Test
    void isIdempotentOnDuplicateEventId() {
        productMetricsService.applyLike("evt-dup", 100L, 1L);
        productMetricsService.applyLike("evt-dup", 100L, 1L);

        assertThat(productMetricsService_likeCount(100L)).isEqualTo(1L);
    }

    @DisplayName("조회 이벤트를 누적 반영한다.")
    @Test
    void aggregatesView() {
        productMetricsService.applyView("evt-v1", 100L);
        productMetricsService.applyView("evt-v2", 100L);

        assertThat(productMetricsRepository.find(100L).orElseThrow().getViewCount()).isEqualTo(2L);
    }

    @DisplayName("주문 결제 이벤트의 각 품목 수량만큼 판매 수를 누적한다.")
    @Test
    void aggregatesSalesPerItem() {
        productMetricsService.applyOrderPaid("evt-o1", List.of(
                new ProductMetricsService.OrderItem(100L, 2L, 20000L),
                new ProductMetricsService.OrderItem(200L, 3L, 30000L)
        ));

        assertThat(productMetricsRepository.find(100L).orElseThrow().getSalesCount()).isEqualTo(2L);
        assertThat(productMetricsRepository.find(200L).orElseThrow().getSalesCount()).isEqualTo(3L);
    }

    @DisplayName("좋아요 수는 음수가 되지 않는다.")
    @Test
    void likeNeverNegative() {
        productMetricsService.applyLike("evt-n1", 100L, -1L);

        assertThat(productMetricsService_likeCount(100L)).isEqualTo(0L);
    }

    private long productMetricsService_likeCount(Long productId) {
        return productMetricsRepository.find(productId).orElseThrow().getLikeCount();
    }
}