package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductMetricsConsumerTest {

    @Mock
    private ProductMetricsService productMetricsService;

    private ProductMetricsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProductMetricsConsumer(productMetricsService, new ObjectMapper());
    }

    @DisplayName("LIKE_CHANGED(LIKED) 메시지는 applyLike(+1) 로 라우팅된다.")
    @Test
    void routesLikedToApplyLike() {
        String message = """
                {"eventId":"e1","eventType":"LIKE_CHANGED","aggregateId":100,
                 "payload":{"productId":100,"action":"LIKED"}}""";

        consumer.handle(message);

        verify(productMetricsService).applyLike("e1", 100L, 1L);
    }

    @DisplayName("LIKE_CHANGED(UNLIKED) 메시지는 applyLike(-1) 로 라우팅된다.")
    @Test
    void routesUnlikedToApplyLikeNegative() {
        String message = """
                {"eventId":"e1","eventType":"LIKE_CHANGED","aggregateId":100,
                 "payload":{"productId":100,"action":"UNLIKED"}}""";

        consumer.handle(message);

        verify(productMetricsService).applyLike("e1", 100L, -1L);
    }

    @DisplayName("PRODUCT_VIEWED 메시지는 applyView 로 라우팅된다.")
    @Test
    void routesViewToApplyView() {
        String message = """
                {"eventId":"e2","eventType":"PRODUCT_VIEWED","aggregateId":100,
                 "payload":{"productId":100}}""";

        consumer.handle(message);

        verify(productMetricsService).applyView("e2", 100L);
    }

    @DisplayName("ORDER_PAID 메시지는 각 품목을 담아 applyOrderPaid 로 라우팅된다.")
    @Test
    void routesOrderPaidToApplyOrderPaid() {
        String message = """
                {"eventId":"e3","eventType":"ORDER_PAID","aggregateId":999,
                 "payload":{"orderId":999,"items":[{"productId":100,"quantity":2},{"productId":200,"quantity":3}]}}""";

        consumer.handle(message);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductMetricsService.OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(productMetricsService).applyOrderPaid(eq("e3"), captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new ProductMetricsService.OrderItem(100L, 2L),
                new ProductMetricsService.OrderItem(200L, 3L)
        );
    }
}