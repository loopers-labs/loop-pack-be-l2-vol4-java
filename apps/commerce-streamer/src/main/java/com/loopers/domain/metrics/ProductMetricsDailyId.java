package com.loopers.domain.metrics;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class ProductMetricsDailyId implements Serializable {

    private LocalDate metricDate;
    private Long productId;

    protected ProductMetricsDailyId() {}

    public ProductMetricsDailyId(LocalDate metricDate, Long productId) {
        this.metricDate = metricDate;
        this.productId = productId;
    }

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public Long getProductId() {
        return productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductMetricsDailyId that)) return false;
        return Objects.equals(metricDate, that.metricDate)
            && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricDate, productId);
    }
}
