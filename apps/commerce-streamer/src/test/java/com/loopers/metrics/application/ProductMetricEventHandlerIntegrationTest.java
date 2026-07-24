package com.loopers.metrics.application;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
    "commerce.metrics.catalog.auto-startup=false"
})
class ProductMetricEventHandlerIntegrationTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-13T10:30:00+09:00");
    private static final LocalDate METRIC_DATE = LocalDate.of(2026, 7, 13);
    private static final EventHandlingMetadata METADATA = new EventHandlingMetadata("catalog-events", 0, 10L);

    private final ProductMetricEventHandler handler;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductMetricEventHandlerIntegrationTest(
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

    @DisplayName("같은 eventId를 다시 처리하면 일간 및 시간 지표를 중복 반영하지 않는다.")
    @Test
    void skipsDailyAndHourlyMetrics_whenEventIsHandledTwice() {
        // arrange
        CatalogEventEnvelope event = viewedEvent("event-1");

        // act
        handler.handle(event, METADATA);
        handler.handle(event, METADATA);

        // assert
        assertThat(handledEventCount()).isEqualTo(1);
        assertThat(viewCount(METRIC_DATE)).isEqualTo(1);
        assertThat(hourlyViewCount()).isEqualTo(1);
    }

    @DisplayName("상품 주문 이벤트를 처리하면 일간 및 시간 단위 주문 수량과 금액을 함께 저장한다.")
    @Test
    void storesDailyAndHourlyOrderMetrics_whenProductOrdered() {
        // arrange
        CatalogEventEnvelope event = orderedEvent("event-1");

        // act
        handler.handle(event, METADATA);

        // assert
        assertThat(handledEventCount()).isEqualTo(1);
        assertThat(dailyOrderMetric()).isEqualTo(new OrderMetric(2, 25_000));
        assertThat(hourlyOrderMetric()).isEqualTo(new HourlyOrderMetric(2, 25_000));
    }

    @DisplayName("같은 상품과 시간 Window의 이벤트 Batch를 합산하여 저장한다.")
    @Test
    void storesAggregatedMetrics_whenBatchHasSameProductAndWindow() {
        // arrange
        ProductMetricEventCommand first = command(viewedEvent("event-1"), 10L);
        ProductMetricEventCommand second = command(viewedEvent("event-2"), 11L);

        // act
        handler.handleBatch(List.of(first, second));

        // assert
        assertAll(
            () -> assertThat(handledEventCount()).isEqualTo(2),
            () -> assertThat(viewCount(METRIC_DATE)).isEqualTo(2),
            () -> assertThat(hourlyViewCount()).isEqualTo(2)
        );
    }

    @DisplayName("같은 상품과 서울 날짜의 서로 다른 시간 이벤트는 일간 한 행과 시간별 두 행에 저장한다.")
    @Test
    void aggregatesDailyMetricAndSeparatesHourlyMetrics() {
        // arrange
        ProductMetricEventCommand firstHour = command(viewedEvent("event-1", OCCURRED_AT), 10L);
        ProductMetricEventCommand nextHour = command(viewedEvent("event-2", OCCURRED_AT.plusHours(1)), 11L);

        // act
        handler.handleBatch(List.of(firstHour, nextHour));

        // assert
        assertAll(
            () -> assertThat(dailyMetricCount()).isEqualTo(1),
            () -> assertThat(viewCount(METRIC_DATE)).isEqualTo(2),
            () -> assertThat(hourlyMetricCount()).isEqualTo(2),
            () -> assertThat(hourlyViewCount()).isEqualTo(2)
        );
    }

    @DisplayName("같은 상품의 서로 다른 서울 날짜 이벤트는 일간 지표를 날짜별로 분리한다.")
    @Test
    void separatesDailyMetricsBySeoulDate() {
        // arrange
        ProductMetricEventCommand firstDate = command(viewedEvent("event-1", OCCURRED_AT), 10L);
        ProductMetricEventCommand nextDate = command(viewedEvent("event-2", OCCURRED_AT.plusDays(1)), 11L);

        // act
        handler.handleBatch(List.of(firstDate, nextDate));

        // assert
        assertAll(
            () -> assertThat(dailyMetricCount()).isEqualTo(2),
            () -> assertThat(viewCount(METRIC_DATE)).isEqualTo(1),
            () -> assertThat(viewCount(METRIC_DATE.plusDays(1))).isEqualTo(1)
        );
    }

    private ProductMetricEventCommand command(CatalogEventEnvelope event, long offset) {
        return new ProductMetricEventCommand(
            event,
            new EventHandlingMetadata("catalog-events", 0, offset)
        );
    }

    private CatalogEventEnvelope viewedEvent(String eventId) {
        return viewedEvent(eventId, OCCURRED_AT);
    }

    private CatalogEventEnvelope viewedEvent(String eventId, ZonedDateTime occurredAt) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_VIEWED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, 1L, null),
            occurredAt
        );
    }

    private CatalogEventEnvelope orderedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_ORDERED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(
                101L,
                1L,
                null,
                null,
                500L,
                2,
                12_500L,
                25_000L
            ),
            OCCURRED_AT
        );
    }

    private int handledEventCount() {
        return jdbcTemplate.queryForObject("select count(*) from event_handled", Integer.class);
    }

    private long hourlyViewCount() {
        return jdbcTemplate.queryForObject(
            "select coalesce(sum(view_count), 0) from product_metric_hourly where product_id = ?",
            Long.class,
            101L
        );
    }

    private long viewCount(LocalDate metricDate) {
        return jdbcTemplate.queryForObject(
            """
                select view_count
                from product_metrics
                where metric_date = ?
                  and product_id = ?
                """,
            Long.class,
            metricDate,
            101L
        );
    }

    private OrderMetric dailyOrderMetric() {
        return jdbcTemplate.queryForObject(
            """
                select order_quantity, order_amount
                from product_metrics
                where metric_date = ?
                  and product_id = ?
                """,
            (resultSet, rowNumber) -> new OrderMetric(
                resultSet.getLong("order_quantity"),
                resultSet.getLong("order_amount")
            ),
            METRIC_DATE,
            101L
        );
    }

    private int dailyMetricCount() {
        return jdbcTemplate.queryForObject(
            "select count(*) from product_metrics where product_id = ?",
            Integer.class,
            101L
        );
    }

    private int hourlyMetricCount() {
        return jdbcTemplate.queryForObject(
            "select count(*) from product_metric_hourly where product_id = ?",
            Integer.class,
            101L
        );
    }

    private HourlyOrderMetric hourlyOrderMetric() {
        return jdbcTemplate.queryForObject(
            """
                select order_quantity, order_amount
                from product_metric_hourly
                where product_id = ?
                """,
            (resultSet, rowNumber) -> new HourlyOrderMetric(
                resultSet.getLong("order_quantity"),
                resultSet.getLong("order_amount")
            ),
            101L
        );
    }

    private record HourlyOrderMetric(long orderQuantity, long orderAmount) {
    }

    private record OrderMetric(long orderQuantity, long orderAmount) {
    }
}
