package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@IdClass(ProductMetricsDailyId.class)
@Table(name = "product_metrics_daily")
public class ProductMetricsDaily {

    @Id
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sale_count", nullable = false)
    private long saleCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "order_score", nullable = false)
    private double orderScore;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetricsDaily() {}
}
