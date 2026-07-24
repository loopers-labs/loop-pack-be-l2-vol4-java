package com.loopers.metrics.application;

import com.loopers.ranking.RankingWindow;

import java.time.LocalDateTime;
import java.util.Objects;

public record ProductMetricHourlyDelta(
    LocalDateTime windowStart,
    Long productId,
    long viewCountDelta,
    long likeDelta,
    long orderQuantityDelta,
    long orderAmountDelta
) {

    public ProductMetricHourlyDelta {
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
    }

    public static ProductMetricHourlyDelta from(CatalogEventEnvelope event) {
        RankingWindow window = RankingWindow.from(event.occurredAt());
        ProductMetricDelta metricDelta = ProductMetricDelta.from(event);

        return new ProductMetricHourlyDelta(
            window.date().atTime(window.hour(), 0),
            metricDelta.productId(),
            metricDelta.viewCountDelta(),
            metricDelta.likeDelta(),
            metricDelta.orderQuantityDelta(),
            metricDelta.orderAmountDelta()
        );
    }

    public ProductMetricHourlyDelta plus(ProductMetricHourlyDelta other) {
        if (!productId.equals(other.productId())) {
            throw new IllegalArgumentException("productId must match to add hourly metric deltas");
        }
        if (!windowStart.equals(other.windowStart())) {
            throw new IllegalArgumentException("windowStart must match to add hourly metric deltas");
        }

        return new ProductMetricHourlyDelta(
            windowStart,
            productId,
            viewCountDelta + other.viewCountDelta(),
            likeDelta + other.likeDelta(),
            orderQuantityDelta + other.orderQuantityDelta(),
            orderAmountDelta + other.orderAmountDelta()
        );
    }
}
