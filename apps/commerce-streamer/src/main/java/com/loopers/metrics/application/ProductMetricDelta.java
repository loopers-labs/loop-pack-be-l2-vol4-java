package com.loopers.metrics.application;

import com.loopers.ranking.RankingWindow;

import java.time.LocalDate;
import java.util.Objects;

public record ProductMetricDelta(
    LocalDate metricDate,
    Long productId,
    long viewCountDelta,
    long likeDelta,
    long orderQuantityDelta,
    long orderAmountDelta
) {

    public ProductMetricDelta {
        Objects.requireNonNull(metricDate, "metricDate must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
    }

    public static ProductMetricDelta from(CatalogEventEnvelope event) {
        LocalDate metricDate = RankingWindow.from(event.occurredAt()).date();
        Long productId = event.payload().productId();

        return switch (event.eventType()) {
            case PRODUCT_VIEWED -> new ProductMetricDelta(metricDate, productId, 1, 0, 0, 0);
            case PRODUCT_LIKED, PRODUCT_UNLIKED ->
                new ProductMetricDelta(metricDate, productId, 0, requiredDelta(event), 0, 0);
            case PRODUCT_ORDERED -> {
                ProductOrderEventData order = ProductOrderEventData.from(event);
                yield new ProductMetricDelta(
                    metricDate,
                    order.productId(),
                    0,
                    0,
                    order.quantity(),
                    order.totalPrice()
                );
            }
        };
    }

    public ProductMetricDelta plus(ProductMetricDelta other) {
        if (!metricDate.equals(other.metricDate())) {
            throw new IllegalArgumentException("metricDate must match to add metric deltas");
        }
        if (!productId.equals(other.productId())) {
            throw new IllegalArgumentException("productId must match to add metric deltas");
        }

        return new ProductMetricDelta(
            metricDate,
            productId,
            viewCountDelta + other.viewCountDelta(),
            likeDelta + other.likeDelta(),
            orderQuantityDelta + other.orderQuantityDelta(),
            orderAmountDelta + other.orderAmountDelta()
        );
    }

    private static long requiredDelta(CatalogEventEnvelope event) {
        Integer delta = event.payload().delta();
        if (delta == null) {
            throw new IllegalArgumentException("delta must not be null for like metric event");
        }
        return delta;
    }
}
