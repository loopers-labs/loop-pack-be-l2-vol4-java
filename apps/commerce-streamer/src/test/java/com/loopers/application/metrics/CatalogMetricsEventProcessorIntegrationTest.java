package com.loopers.application.metrics;

import com.loopers.infrastructure.metrics.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.admin.auto-create=false"
})
class CatalogMetricsEventProcessorIntegrationTest {

    private final CatalogMetricsEventProcessor processor;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CatalogMetricsEventProcessorIntegrationTest(
        CatalogMetricsEventProcessor processor,
        ProductMetricsJpaRepository productMetricsJpaRepository,
        EventHandledJpaRepository eventHandledJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.processor = processor;
        this.productMetricsJpaRepository = productMetricsJpaRepository;
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 eventId를 두 번 처리해도 product_metrics는 한 번만 반영한다.")
    @Test
    void handlesEventIdempotently_whenSameEventIsDeliveredTwice() {
        // arrange
        CatalogEventMessage event = likedEvent("event-1");

        // act
        boolean firstProcessed = processor.process(event);
        boolean secondProcessed = processor.process(event);

        // assert
        var metrics = productMetricsJpaRepository.findByProductId(1L).orElseThrow();
        assertAll(
            () -> assertThat(firstProcessed).isTrue(),
            () -> assertThat(secondProcessed).isFalse(),
            () -> assertThat(metrics.getLikeCount()).isEqualTo(1),
            () -> assertThat(eventHandledJpaRepository.existsById("event-1")).isTrue()
        );
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
