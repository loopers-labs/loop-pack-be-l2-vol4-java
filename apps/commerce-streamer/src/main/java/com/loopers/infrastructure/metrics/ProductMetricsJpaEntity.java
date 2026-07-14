package com.loopers.infrastructure.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "product_metrics",
    indexes = @Index(name = "idx_product_metrics_product_id", columnList = "product_id", unique = true)
)
public class ProductMetricsJpaEntity extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    protected ProductMetricsJpaEntity() {
    }

    private ProductMetricsJpaEntity(Long productId) {
        this.productId = productId;
        this.likeCount = 0L;
        this.viewCount = 0L;
        this.salesCount = 0L;
    }

    public static ProductMetricsJpaEntity create(Long productId) {
        return new ProductMetricsJpaEntity(productId);
    }

    public void applyLikeDelta(int delta) {
        this.likeCount = Math.max(0L, this.likeCount + delta);
    }

    public void applyViewDelta(int delta) {
        this.viewCount = Math.max(0L, this.viewCount + delta);
    }

    public void applySalesDelta(int delta) {
        this.salesCount = Math.max(0L, this.salesCount + delta);
    }

    public Long getProductId() {
        return productId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public long getSalesCount() {
        return salesCount;
    }
}
