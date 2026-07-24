package com.loopers.metrics.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductMetricDeltaTest {

    private static final LocalDate METRIC_DATE = LocalDate.of(2026, 7, 2);

    @DisplayName("같은 날짜와 상품의 지표 Delta를 항목별로 합산한다")
    @Test
    void addsMetricDeltasForSameDateAndProduct() {
        // arrange
        ProductMetricDelta first = new ProductMetricDelta(METRIC_DATE, 101L, 2, 1, 3, 30_000);
        ProductMetricDelta second = new ProductMetricDelta(METRIC_DATE, 101L, 4, -1, 5, 50_000);

        // act
        ProductMetricDelta result = first.plus(second);

        // assert
        assertThat(result).isEqualTo(
            new ProductMetricDelta(METRIC_DATE, 101L, 6, 0, 8, 80_000)
        );
    }

    @DisplayName("서로 다른 날짜의 지표 Delta는 합산하지 않는다")
    @Test
    void rejectsMetricDeltasForDifferentDates() {
        // arrange
        ProductMetricDelta first = new ProductMetricDelta(METRIC_DATE, 101L, 2, 1, 3, 30_000);
        ProductMetricDelta second = new ProductMetricDelta(METRIC_DATE.plusDays(1), 101L, 4, -1, 5, 50_000);

        // act & assert
        assertThatThrownBy(() -> first.plus(second))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("metricDate");
    }

    @DisplayName("서로 다른 상품의 지표 Delta는 합산하지 않는다")
    @Test
    void rejectsMetricDeltasForDifferentProducts() {
        // arrange
        ProductMetricDelta first = new ProductMetricDelta(METRIC_DATE, 101L, 2, 1, 3, 30_000);
        ProductMetricDelta second = new ProductMetricDelta(METRIC_DATE, 202L, 4, -1, 5, 50_000);

        // act & assert
        assertThatThrownBy(() -> first.plus(second))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("productId");
    }

    @DisplayName("이벤트 발생 시각을 서울 날짜로 바꾸어 일간 지표를 만든다")
    @Test
    void createsMetricForSeoulDate() {
        // arrange
        CatalogEventEnvelope event = new CatalogEventEnvelope(
            "event-1",
            CatalogEventType.PRODUCT_VIEWED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, 10L, null),
            ZonedDateTime.parse("2026-07-01T16:00:00Z")
        );

        // act
        ProductMetricDelta result = ProductMetricDelta.from(event);

        // assert
        assertThat(result).isEqualTo(
            new ProductMetricDelta(LocalDate.of(2026, 7, 2), 101L, 1, 0, 0, 0)
        );
    }

    @DisplayName("주문 이벤트의 수량과 총액을 일간 지표로 만든다")
    @Test
    void createsOrderQuantityAndAmountMetric() {
        // arrange
        CatalogEventEnvelope event = new CatalogEventEnvelope(
            "event-1",
            CatalogEventType.PRODUCT_ORDERED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, null, null, 500L, 2, 12_500L, 25_000L),
            ZonedDateTime.parse("2026-07-02T10:00:00+09:00")
        );

        // act
        ProductMetricDelta result = ProductMetricDelta.from(event);

        // assert
        assertThat(result).isEqualTo(
            new ProductMetricDelta(METRIC_DATE, 101L, 0, 0, 2, 25_000)
        );
    }
}
