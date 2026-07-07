package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsProcessorTest {

    private final ProductMetricsRepository metricsRepository = mock(ProductMetricsRepository.class);
    private final EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
    private final MetricsProcessor processor = new MetricsProcessor(metricsRepository, eventHandledRepository);

    @DisplayName("새 catalog 이벤트면 applyLike + event_handled 기록.")
    @Test
    void handlesNewCatalog() {
        when(eventHandledRepository.existsByEventId("evt-1")).thenReturn(false);
        processor.handleCatalog(new CatalogEventMessage("evt-1", "LikeAdded", 100L, 5L, 3L, "t"));
        verify(metricsRepository).applyLike(100L, 5L, 3L);
        verify(eventHandledRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @DisplayName("이미 처리한 이벤트면 skip.")
    @Test
    void skipsDuplicate() {
        when(eventHandledRepository.existsByEventId("evt-1")).thenReturn(true);
        processor.handleCatalog(new CatalogEventMessage("evt-1", "LikeAdded", 100L, 5L, 3L, "t"));
        verify(metricsRepository, never()).applyLike(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @DisplayName("ProductViewed 이벤트면 addView + event_handled 기록 (applyLike 아님).")
    @Test
    void handlesProductViewed() {
        when(eventHandledRepository.existsByEventId("evt-v1")).thenReturn(false);
        processor.handleCatalog(new CatalogEventMessage("evt-v1", "ProductViewed", 100L, 0L, 0L, "t"));
        verify(metricsRepository).addView(100L);
        verify(metricsRepository, never()).applyLike(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(eventHandledRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @DisplayName("이미 처리한 ProductViewed 면 addView 하지 않는다(멱등).")
    @Test
    void skipsDuplicateProductViewed() {
        when(eventHandledRepository.existsByEventId("evt-v1")).thenReturn(true);
        processor.handleCatalog(new CatalogEventMessage("evt-v1", "ProductViewed", 100L, 0L, 0L, "t"));
        verify(metricsRepository, never()).addView(org.mockito.ArgumentMatchers.anyLong());
    }

    @DisplayName("order 이벤트면 라인별 addSales.")
    @Test
    void handlesOrderLines() {
        when(eventHandledRepository.existsByEventId("evt-2")).thenReturn(false);
        processor.handleOrder(new OrderEventMessage("evt-2", "OrderPlaced", 55L,
            List.of(new OrderEventMessage.Line(11L, 2), new OrderEventMessage.Line(12L, 1)), "t"));
        verify(metricsRepository).addSales(11L, 2);
        verify(metricsRepository).addSales(12L, 1);
    }
}
