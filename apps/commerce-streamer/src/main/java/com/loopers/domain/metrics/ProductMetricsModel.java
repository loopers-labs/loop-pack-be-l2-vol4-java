package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "product_metrics")
public class ProductMetricsModel {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    protected ProductMetricsModel() {}

    public static ProductMetricsModel create(Long productId) {
        ProductMetricsModel model = new ProductMetricsModel();
        model.productId = productId;
        model.likeCount = 0;
        model.viewCount = 0;
        model.salesCount = 0;
        model.updatedAt = LocalDateTime.now();
        return model;
    }

    public void incrementLikeCount() {
        this.likeCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void decrementLikeCount() {
        this.likeCount = Math.max(0, this.likeCount - 1);
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementViewCount() {
        this.viewCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void addSalesCount(long quantity) {
        this.salesCount += quantity;
        this.updatedAt = LocalDateTime.now();
    }
}
