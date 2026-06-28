package com.loopers.infrastructure.catalog.metrics;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.catalog.metrics.ProductMetrics;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "product_metrics",
    indexes = {
        @Index(name = "uk_product_metrics_product_id", columnList = "product_id", unique = true)
    }
)
public class ProductMetricsJpaEntity extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "sales_count", nullable = false)
    private Long salesCount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "last_like_event_at")
    private ZonedDateTime lastLikeEventAt;

    protected ProductMetricsJpaEntity() {}

    private ProductMetricsJpaEntity(
        Long productId,
        Long likeCount,
        Long salesCount,
        Long viewCount,
        ZonedDateTime lastLikeEventAt
    ) {
        this.productId = productId;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.viewCount = viewCount;
        this.lastLikeEventAt = lastLikeEventAt;
    }

    public static ProductMetricsJpaEntity from(ProductMetrics metrics) {
        return new ProductMetricsJpaEntity(
            metrics.getProductId(),
            metrics.getLikeCount(),
            metrics.getSalesCount(),
            metrics.getViewCount(),
            metrics.getLastLikeEventAt()
        );
    }

    public ProductMetrics toDomain() {
        return ProductMetrics.reconstruct(
            getId(),
            productId,
            likeCount,
            salesCount,
            viewCount,
            lastLikeEventAt,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(ProductMetrics metrics) {
        this.productId = metrics.getProductId();
        this.likeCount = metrics.getLikeCount();
        this.salesCount = metrics.getSalesCount();
        this.viewCount = metrics.getViewCount();
        this.lastLikeEventAt = metrics.getLastLikeEventAt();
    }
}
