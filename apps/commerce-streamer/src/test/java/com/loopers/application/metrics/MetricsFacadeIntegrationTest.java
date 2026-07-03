package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class MetricsFacadeIntegrationTest {

    @Autowired
    private MetricsFacade metricsFacade;

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductMetricsModel metricsOf(Long productId) {
        return productMetricsRepository.findByProductId(productId).orElseThrow();
    }

    @DisplayName("좋아요 이벤트를 적용할 때,")
    @Nested
    class ApplyLike {

        @DisplayName("같은 이벤트를 두 번 적용해도 like_count는 한 번만 반영된다.")
        @Test
        void isIdempotent_whenSameEventAppliedTwice() {
            // given
            Long productId = 1L;
            String eventId = UUID.randomUUID().toString();

            // when
            metricsFacade.applyLike(eventId, productId);
            metricsFacade.applyLike(eventId, productId);

            // then
            assertThat(metricsOf(productId).getLikeCount()).isEqualTo(1L);
        }
    }

    @DisplayName("좋아요 취소 이벤트를 적용할 때,")
    @Nested
    class ApplyUnlike {

        @DisplayName("like_count가 1 감소한다.")
        @Test
        void decreasesLikeCount() {
            // given
            Long productId = 2L;
            metricsFacade.applyLike(UUID.randomUUID().toString(), productId);

            // when
            metricsFacade.applyUnlike(UUID.randomUUID().toString(), productId);

            // then
            assertThat(metricsOf(productId).getLikeCount()).isEqualTo(0L);
        }
    }

    @DisplayName("조회 이벤트를 적용할 때,")
    @Nested
    class ApplyView {

        @DisplayName("같은 이벤트를 두 번 적용해도 view_count는 한 번만 반영된다.")
        @Test
        void isIdempotent_whenSameEventAppliedTwice() {
            // given
            Long productId = 3L;
            String eventId = UUID.randomUUID().toString();

            // when
            metricsFacade.applyView(eventId, productId);
            metricsFacade.applyView(eventId, productId);

            // then
            assertThat(metricsOf(productId).getViewCount()).isEqualTo(1L);
        }
    }

    @DisplayName("판매 이벤트를 적용할 때,")
    @Nested
    class ApplySales {

        @DisplayName("주문 아이템 여러 개가 한 이벤트로 각 상품에 정확히 반영된다.")
        @Test
        void appliesEachItem_whenOrderHasMultipleItems() {
            // given
            Long productIdA = 4L;
            Long productIdB = 5L;
            String eventId = UUID.randomUUID().toString();
            List<MetricsFacade.SalesItem> items = List.of(
                    new MetricsFacade.SalesItem(productIdA, 2L),
                    new MetricsFacade.SalesItem(productIdB, 3L)
            );

            // when
            metricsFacade.applySales(eventId, items);

            // then
            assertAll(
                    () -> assertThat(metricsOf(productIdA).getSalesCount()).isEqualTo(2L),
                    () -> assertThat(metricsOf(productIdB).getSalesCount()).isEqualTo(3L)
            );
        }

        @DisplayName("같은 이벤트(주문)를 두 번 적용해도 sales_count는 한 번만 반영된다.")
        @Test
        void isIdempotent_whenSameOrderEventAppliedTwice() {
            // given
            Long productId = 6L;
            String eventId = UUID.randomUUID().toString();
            List<MetricsFacade.SalesItem> items = List.of(new MetricsFacade.SalesItem(productId, 5L));

            // when
            metricsFacade.applySales(eventId, items);
            metricsFacade.applySales(eventId, items);

            // then
            assertThat(metricsOf(productId).getSalesCount()).isEqualTo(5L);
        }
    }
}
