package com.loopers.metrics.application;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
    "commerce.metrics.catalog.auto-startup=false"
})
class ProductMetricEventHandlerRollbackIntegrationTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-13T10:30:00+09:00");
    private static final EventHandlingMetadata METADATA = new EventHandlingMetadata("catalog-events", 0, 10L);

    private final ProductMetricEventHandler handler;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private ProductMetricHourlyRepository productMetricHourlyRepository;

    @Autowired
    ProductMetricEventHandlerRollbackIntegrationTest(
        ProductMetricEventHandler handler,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.handler = handler;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("시간 지표 저장에 실패하면 처리 이력과 일간 지표도 함께 롤백한다.")
    @Test
    void rollsBackHandledEventAndDailyMetric_whenHourlyMetricFails() {
        // arrange
        CatalogEventEnvelope event = new CatalogEventEnvelope(
            "event-1",
            CatalogEventType.PRODUCT_VIEWED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, 1L, null),
            OCCURRED_AT
        );
        doThrow(new IllegalStateException("hourly metric save failed"))
            .when(productMetricHourlyRepository)
            .addAll(any(), any());

        // act & assert
        assertThatThrownBy(() -> handler.handle(event, METADATA))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("hourly metric save failed");
        assertAll(
            () -> assertThat(countRows("event_handled")).isZero(),
            () -> assertThat(countRows("product_metrics")).isZero()
        );
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }
}
