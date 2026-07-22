package com.loopers.domain.catalog.metrics;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public class ProductMetrics {

    private Long id = 0L;
    private LocalDate metricDate;
    private Long productId;
    private Long likeCount;
    private Long salesCount;
    private Long salesAmount;
    private Long viewCount;
    private ZonedDateTime lastLikeEventAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    public ProductMetrics(LocalDate metricDate, Long productId) {
        if (metricDate == null) {
            throw new IllegalArgumentException("상품 메트릭 집계일은 필수입니다.");
        }
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        this.metricDate = metricDate;
        this.productId = productId;
        this.likeCount = 0L;
        this.salesCount = 0L;
        this.salesAmount = 0L;
        this.viewCount = 0L;
    }

    public static ProductMetrics reconstruct(
        Long id,
        LocalDate metricDate,
        Long productId,
        Long likeCount,
        Long salesCount,
        Long salesAmount,
        Long viewCount,
        ZonedDateTime lastLikeEventAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        ProductMetrics metrics = new ProductMetrics(metricDate, productId);
        metrics.likeCount = likeCount == null ? 0L : likeCount;
        metrics.salesCount = salesCount == null ? 0L : salesCount;
        metrics.salesAmount = salesAmount == null ? 0L : salesAmount;
        metrics.viewCount = viewCount == null ? 0L : viewCount;
        metrics.lastLikeEventAt = lastLikeEventAt;
        metrics.id = id == null ? 0L : id;
        metrics.createdAt = createdAt;
        metrics.updatedAt = updatedAt;
        metrics.deletedAt = deletedAt;
        return metrics;
    }

    public void increaseViewCount() {
        this.viewCount += 1L;
    }

    public void applyLikeDelta(long delta, ZonedDateTime eventAt) {
        if (eventAt == null) {
            throw new IllegalArgumentException("좋아요 이벤트 시각은 필수입니다.");
        }
        this.likeCount += delta;
        if (lastLikeEventAt == null || eventAt.isAfter(lastLikeEventAt)) {
            this.lastLikeEventAt = eventAt;
        }
    }

    public void increaseSales(Integer quantity, Long lineAmount) {
        if (quantity == null || quantity <= 0) {
            return;
        }
        long normalizedLineAmount = lineAmount == null ? 0L : lineAmount;
        if (normalizedLineAmount < 0) {
            throw new IllegalArgumentException("판매 금액은 0 이상이어야 합니다.");
        }

        this.salesCount += quantity;
        this.salesAmount += normalizedLineAmount;
    }

    public boolean isNew() {
        return id == null || id == 0L;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getMetricDate() {
        return metricDate;
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

    public Long getSalesAmount() {
        return salesAmount;
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
