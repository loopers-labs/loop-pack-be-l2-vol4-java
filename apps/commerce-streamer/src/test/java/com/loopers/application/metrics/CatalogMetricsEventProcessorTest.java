package com.loopers.application.metrics;

import com.loopers.infrastructure.metrics.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaEntity;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogMetricsEventProcessorTest {

    @DisplayName("처리하지 않은 좋아요 이벤트면 product_metrics를 upsert하고 event_handled를 기록한다.")
    @Test
    void upsertsProductMetricsAndMarksHandled_whenEventIsNew() {
        // arrange
        EventHandledJpaRepository eventHandledJpaRepository = mock(EventHandledJpaRepository.class);
        ProductMetricsJpaRepository productMetricsJpaRepository = mock(ProductMetricsJpaRepository.class);
        when(eventHandledJpaRepository.existsById("event-1")).thenReturn(false);
        when(productMetricsJpaRepository.findByProductId(1L)).thenReturn(Optional.empty());
        CatalogMetricsEventProcessor processor = new CatalogMetricsEventProcessor(
            eventHandledJpaRepository,
            productMetricsJpaRepository,
            new SimpleMeterRegistry()
        );

        // act
        boolean processed = processor.process(likedEvent("event-1"));

        // assert
        assertThat(processed).isTrue();
        verify(productMetricsJpaRepository).save(any(ProductMetricsJpaEntity.class));
        verify(eventHandledJpaRepository).save(any());
    }

    @DisplayName("이미 처리한 eventId면 product_metrics를 다시 변경하지 않는다.")
    @Test
    void skipsProductMetricsUpdate_whenEventWasHandled() {
        // arrange
        EventHandledJpaRepository eventHandledJpaRepository = mock(EventHandledJpaRepository.class);
        ProductMetricsJpaRepository productMetricsJpaRepository = mock(ProductMetricsJpaRepository.class);
        when(eventHandledJpaRepository.existsById("event-1")).thenReturn(true);
        CatalogMetricsEventProcessor processor = new CatalogMetricsEventProcessor(
            eventHandledJpaRepository,
            productMetricsJpaRepository,
            new SimpleMeterRegistry()
        );

        // act
        boolean processed = processor.process(likedEvent("event-1"));

        // assert
        assertThat(processed).isFalse();
        verify(productMetricsJpaRepository, never()).save(any());
        verify(eventHandledJpaRepository, never()).save(any());
    }

    private CatalogEventMessage likedEvent(String eventId) {
        return new CatalogEventMessage(
            eventId,
            "PRODUCT_LIKED",
            "PRODUCT",
            1L,
            ZonedDateTime.now(),
            Map.of("likeCountDelta", 1)
        );
    }
}
