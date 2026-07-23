package com.loopers.infrastructure.metrics;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * daily_product_metrics 의 복합 식별자 — (product_id, metric_date).
 */
public class DailyProductMetricsId implements Serializable {

    private Long productId;
    private LocalDate metricDate;

    protected DailyProductMetricsId() {}

    public DailyProductMetricsId(Long productId, LocalDate metricDate) {
        this.productId = productId;
        this.metricDate = metricDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DailyProductMetricsId that)) {
            return false;
        }
        return Objects.equals(productId, that.productId) && Objects.equals(metricDate, that.metricDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, metricDate);
    }
}
