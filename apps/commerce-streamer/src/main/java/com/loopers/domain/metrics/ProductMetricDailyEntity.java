package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public class ProductMetricDailyEntity {

    private String productId;
    private LocalDate metricDate;
    private long viewCount;
    private long likeDeltaCount;
    private long purchaseQuantity;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    private ProductMetricDailyEntity() {}

    public static ProductMetricDailyEntity reconstruct(
            String productId,
            LocalDate metricDate,
            long viewCount,
            long likeDeltaCount,
            long purchaseQuantity,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        ProductMetricDailyEntity entity = new ProductMetricDailyEntity();
        entity.productId = productId;
        entity.metricDate = metricDate;
        entity.viewCount = viewCount;
        entity.likeDeltaCount = likeDeltaCount;
        entity.purchaseQuantity = purchaseQuantity;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        return entity;
    }

    public String getProductId() { return productId; }
    public LocalDate getMetricDate() { return metricDate; }
    public long getViewCount() { return viewCount; }
    public long getLikeDeltaCount() { return likeDeltaCount; }
    public long getPurchaseQuantity() { return purchaseQuantity; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
