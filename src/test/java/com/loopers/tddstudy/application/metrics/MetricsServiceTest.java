package com.loopers.tddstudy.application.metrics;

import com.loopers.tddstudy.infrastructure.metrics.EventHandledJpaRepository;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetrics;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.tddstudy.messaging.CatalogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class MetricsServiceTest {

    private ProductMetricsJpaRepository metricsRepository;
    private EventHandledJpaRepository eventHandledRepository;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsRepository = mock(ProductMetricsJpaRepository.class);
        eventHandledRepository = mock(EventHandledJpaRepository.class);
        metricsService = new MetricsService(metricsRepository, eventHandledRepository);
    }

    @Test
    @DisplayName("좋아요 이벤트를 처리하면 like_count 가 증가하고 event_handled 에 기록된다")
    void apply_liked_event() {
        when(eventHandledRepository.existsById("e1")).thenReturn(false);
        when(metricsRepository.findById(1L)).thenReturn(Optional.empty());

        metricsService.apply(new CatalogEvent("e1", "PRODUCT_LIKED", 1L, 1, System.currentTimeMillis()));

        verify(metricsRepository).save(argThat(m -> m.getLikeCount() == 1));
        verify(eventHandledRepository).save(any());
    }

    @Test
    @DisplayName("이미 처리한 event_id 는 무시되어 집계가 중복 반영되지 않는다 (멱등)")
    void apply_is_idempotent() {
        when(eventHandledRepository.existsById("e1")).thenReturn(true);   // 이미 처리됨

        metricsService.apply(new CatalogEvent("e1", "PRODUCT_LIKED", 1L, 1, System.currentTimeMillis()));

        verify(metricsRepository, never()).save(any());     // 집계 변경 없음
        verify(eventHandledRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문 이벤트를 처리하면 sales_count 가 수량만큼 증가한다")
    void apply_sales_event() {
        when(eventHandledRepository.existsById("e2")).thenReturn(false);
        ProductMetrics existing = new ProductMetrics(1L);
        when(metricsRepository.findById(1L)).thenReturn(Optional.of(existing));

        metricsService.apply(new CatalogEvent("e2", "ORDER_SALES", 1L, 3, System.currentTimeMillis()));

        assertThat(existing.getSalesCount()).isEqualTo(3);
        verify(metricsRepository).save(existing);
    }

    @Test
    @DisplayName("이미 반영한 것보다 오래된 이벤트는 무시된다 (version 가드)")
    void ignores_stale_event() {
        when(eventHandledRepository.existsById("old")).thenReturn(false);
        ProductMetrics m = new ProductMetrics(1L);
        m.addLike(5);
        m.markEvent(2000L);                              // 최신 반영 시각 = 2000
        when(metricsRepository.findById(1L)).thenReturn(Optional.of(m));

        metricsService.apply(new CatalogEvent("old", "PRODUCT_LIKED", 1L, 1, 1000L)); // 더 과거(1000)

        assertThat(m.getLikeCount()).isEqualTo(5);        // 증가 안 함
        verify(metricsRepository, never()).save(any());   // 집계 저장 안 함
        verify(eventHandledRepository).save(any());       // 재처리 방지 기록은 함
    }
}
