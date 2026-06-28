package com.loopers.domain.catalog.metrics;

import java.time.ZonedDateTime;

public class ProductMetrics {

    private Long id = 0L;
    private Long productId;
    private Long likeCount;
    private Long salesCount;
    private Long viewCount;
    private ZonedDateTime lastLikeEventAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    public ProductMetrics(Long productId) {
        this.productId = productId;
        this.likeCount = 0L;
        this.salesCount = 0L;
        this.viewCount = 0L;
    }

    public static ProductMetrics reconstruct(
        Long id,
        Long productId,
        Long likeCount,
        Long salesCount,
        Long viewCount,
        ZonedDateTime lastLikeEventAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        ProductMetrics metrics = new ProductMetrics(productId);
        metrics.likeCount = likeCount == null ? 0L : likeCount;
        metrics.salesCount = salesCount == null ? 0L : salesCount;
        metrics.viewCount = viewCount == null ? 0L : viewCount;
        metrics.lastLikeEventAt = lastLikeEventAt;
        metrics.id = id == null ? 0L : id;
        metrics.createdAt = createdAt;
        metrics.updatedAt = updatedAt;
        metrics.deletedAt = deletedAt;
        return metrics;
    }

    public boolean applyLikeCount(Long newLikeCount, ZonedDateTime eventAt) {
        if (newLikeCount == null || newLikeCount < 0) {
            throw new IllegalArgumentException("좋아요 수는 0 이상이어야 합니다.");
        }
        if (eventAt == null) {
            throw new IllegalArgumentException("좋아요 이벤트 시각은 필수입니다.");
        }
        if (lastLikeEventAt != null && eventAt.isBefore(lastLikeEventAt)) {
            return false;
        }

        this.likeCount = newLikeCount;
        this.lastLikeEventAt = eventAt;
        return true;
    }

    public boolean isNew() {
        return id == null || id == 0L;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public Long getSalesCount() {
        return salesCount;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public ZonedDateTime getLastLikeEventAt() {
        return lastLikeEventAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }
}
