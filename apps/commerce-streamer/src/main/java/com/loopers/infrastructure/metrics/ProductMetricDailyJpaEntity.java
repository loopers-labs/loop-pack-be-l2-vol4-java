package com.loopers.infrastructure.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "product_metric_daily")
@Getter
public class ProductMetricDailyJpaEntity {

    @EmbeddedId
    private ProductMetricDailyId id;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_delta_count", nullable = false)
    private long likeDeltaCount;

    @Column(name = "purchase_quantity", nullable = false)
    private long purchaseQuantity;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetricDailyJpaEntity() {}
}
