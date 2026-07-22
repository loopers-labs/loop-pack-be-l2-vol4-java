package com.loopers.infrastructure.catalog.metrics;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.catalog.metrics.ProductMetrics;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(
    name = "product_metrics",
    indexes = {
        @Index(name = "idx_product_metrics_metric_date", columnList = "metric_date")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_metrics_date_product", columnNames = {"metric_date", "product_id"})
    }
)
public class ProductMetricsJpaEntity extends BaseEntity {

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "sales_count", nullable = false)
    private Long salesCount;

    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "last_like_event_at")
    private ZonedDateTime lastLikeEventAt;

    protected ProductMetricsJpaEntity() {}

    private ProductMetricsJpaEntity(
        LocalDate metricDate,
        Long productId,
        Long likeCount,
        Long salesCount,
        Long salesAmount,
        Long viewCount,
        ZonedDateTime lastLikeEventAt
    ) {
        this.metricDate = metricDate;
        this.productId = productId;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.salesAmount = salesAmount;
        this.viewCount = viewCount;
        this.lastLikeEventAt = lastLikeEventAt;
    }

    public static ProductMetricsJpaEntity from(ProductMetrics metrics) {
        return new ProductMetricsJpaEntity(
            metrics.getMetricDate(),
            metrics.getProductId(),
            metrics.getLikeCount(),
            metrics.getSalesCount(),
            metrics.getSalesAmount(),
            metrics.getViewCount(),
            metrics.getLastLikeEventAt()
        );
    }

    public ProductMetrics toDomain() {
        return ProductMetrics.reconstruct(
            getId(),
            metricDate,
            productId,
            likeCount,
            salesCount,
            salesAmount,
            viewCount,
            lastLikeEventAt,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(ProductMetrics metrics) {
        this.metricDate = metrics.getMetricDate();
        this.productId = metrics.getProductId();
        this.likeCount = metrics.getLikeCount();
        this.salesCount = metrics.getSalesCount();
        this.salesAmount = metrics.getSalesAmount();
        this.viewCount = metrics.getViewCount();
        this.lastLikeEventAt = metrics.getLastLikeEventAt();
    }
}
