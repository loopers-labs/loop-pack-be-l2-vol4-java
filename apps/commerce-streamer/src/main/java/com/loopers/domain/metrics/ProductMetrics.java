package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "product_metrics",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_metrics_product", columnNames = {"product_id"}))
public class ProductMetrics extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "like_count", nullable = false)
    private long likeCount;
    @Column(name = "sales_count", nullable = false)
    private long salesCount;
    @Column(name = "view_count", nullable = false)
    private long viewCount;
    @Column(name = "like_version", nullable = false)
    private long likeVersion;

    protected ProductMetrics() {}

    public Long getProductId() { return productId; }
    public long getLikeCount() { return likeCount; }
    public long getSalesCount() { return salesCount; }
    public long getViewCount() { return viewCount; }
    public long getLikeVersion() { return likeVersion; }
}
