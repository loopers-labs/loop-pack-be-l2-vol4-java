package com.loopers.infrastructure.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class ProductMetricDailyId implements Serializable {

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "product_id", length = 60, nullable = false)
    private String productId;

    protected ProductMetricDailyId() {}

    public ProductMetricDailyId(LocalDate metricDate, String productId) {
        this.metricDate = metricDate;
        this.productId = productId;
    }

    public LocalDate getMetricDate() { return metricDate; }
    public String getProductId() { return productId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductMetricDailyId that)) return false;
        return Objects.equals(metricDate, that.metricDate) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricDate, productId);
    }
}
