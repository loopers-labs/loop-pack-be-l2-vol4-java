package com.loopers.domain.metrics;

import java.time.ZonedDateTime;

public class ProductMetricSummaryEntity {

    private String productId;
    private long viewCount;
    private long likeCount;
    private long purchaseCount;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    private ProductMetricSummaryEntity() {}

    public static ProductMetricSummaryEntity of(
            String productId,
            long viewCount,
            long likeCount,
            long purchaseCount,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        ProductMetricSummaryEntity entity = new ProductMetricSummaryEntity();
        entity.productId = productId;
        entity.viewCount = viewCount;
        entity.likeCount = likeCount;
        entity.purchaseCount = purchaseCount;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        return entity;
    }

    public String getProductId() { return productId; }
    public long getViewCount() { return viewCount; }
    public long getLikeCount() { return likeCount; }
    public long getPurchaseCount() { return purchaseCount; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
