package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsProcessorTest {

    // 2026-07-20T23:30Z → KST 로는 다음날 08:30 이므로 일자는 07-21 로 양자화된다.
    private static final String OCCURRED_AT = "2026-07-20T23:30:00Z";
    private static final LocalDate KST_DATE = LocalDate.of(2026, 7, 21);

    private final ProductMetricsRepository metricsRepository = mock(ProductMetricsRepository.class);
    private final EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
    private final MetricsProcessor processor = new MetricsProcessor(metricsRepository, eventHandledRepository);

    @DisplayName("새 catalog 이벤트면 발생시각의 KST 일자로 applyLike + event_handled 기록.")
    @Test
    void handlesNewCatalog() {
        when(eventHandledRepository.existsByEventId("evt-1")).thenReturn(false);
        processor.handleCatalog(new CatalogEventMessage("evt-1", "LikeAdded", 100L, 5L, 3L, OCCURRED_AT));
        verify(metricsRepository).applyLike(100L, KST_DATE, 5L, 3L, 1);
        verify(eventHandledRepository).save(any());
    }

    @DisplayName("LikeRemoved 는 like_delta 를 -1 로 전달한다(좋아요 취소).")
    @Test
    void handlesLikeRemoved() {
        when(eventHandledRepository.existsByEventId("evt-r1")).thenReturn(false);
        processor.handleCatalog(new CatalogEventMessage("evt-r1", "LikeRemoved", 100L, 4L, 4L, OCCURRED_AT));
        verify(metricsRepository).applyLike(100L, KST_DATE, 4L, 4L, -1);
    }

    @DisplayName("이미 처리한 이벤트면 skip.")
    @Test
    void skipsDuplicate() {
        when(eventHandledRepository.existsByEventId("evt-1")).thenReturn(true);
        processor.handleCatalog(new CatalogEventMessage("evt-1", "LikeAdded", 100L, 5L, 3L, OCCURRED_AT));
        verify(metricsRepository, never()).applyLike(anyLong(), any(), anyLong(), anyLong(), anyInt());
    }

    @DisplayName("ProductViewed 이벤트면 addView + event_handled 기록 (applyLike 아님).")
    @Test
    void handlesProductViewed() {
        when(eventHandledRepository.existsByEventId("evt-v1")).thenReturn(false);
        processor.handleCatalog(new CatalogEventMessage("evt-v1", "ProductViewed", 100L, 0L, 0L, OCCURRED_AT));
        verify(metricsRepository).addView(100L, KST_DATE);
        verify(metricsRepository, never()).applyLike(anyLong(), any(), anyLong(), anyLong(), anyInt());
        verify(eventHandledRepository).save(any());
    }

    @DisplayName("이미 처리한 ProductViewed 면 addView 하지 않는다(멱등).")
    @Test
    void skipsDuplicateProductViewed() {
        when(eventHandledRepository.existsByEventId("evt-v1")).thenReturn(true);
        processor.handleCatalog(new CatalogEventMessage("evt-v1", "ProductViewed", 100L, 0L, 0L, OCCURRED_AT));
        verify(metricsRepository, never()).addView(anyLong(), any());
    }

    @DisplayName("order 이벤트면 라인별 addSales.")
    @Test
    void handlesOrderLines() {
        when(eventHandledRepository.existsByEventId("evt-2")).thenReturn(false);
        processor.handleOrder(new OrderEventMessage("evt-2", "OrderPlaced", 55L,
            List.of(new OrderEventMessage.Line(11L, 2), new OrderEventMessage.Line(12L, 1)), OCCURRED_AT));
        verify(metricsRepository).addSales(11L, KST_DATE, 2);
        verify(metricsRepository).addSales(12L, KST_DATE, 1);
    }

    @DisplayName("이미 처리한 order 이벤트면 addSales 하지 않는다(멱등).")
    @Test
    void skipsDuplicateOrder() {
        when(eventHandledRepository.existsByEventId("evt-2")).thenReturn(true);
        processor.handleOrder(new OrderEventMessage("evt-2", "OrderPlaced", 55L,
            List.of(new OrderEventMessage.Line(11L, 2)), OCCURRED_AT));
        verify(metricsRepository, never()).addSales(anyLong(), any(), anyInt());
    }
}
