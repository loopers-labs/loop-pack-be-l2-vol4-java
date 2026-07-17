package com.loopers.infrastructure.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(
    name = "product_metric_hourly",
    indexes = @Index(name = "idx_product_metric_hourly_date_product", columnList = "metric_date, product_id"),
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_metric_hourly_date_hour_product",
        columnNames = {"metric_date", "metric_hour", "product_id"}
    )
)
public class ProductMetricHourlyJpaEntity extends BaseEntity {

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "metric_hour", nullable = false)
    private int metricHour;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    protected ProductMetricHourlyJpaEntity() {
    }

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public int getMetricHour() {
        return metricHour;
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
