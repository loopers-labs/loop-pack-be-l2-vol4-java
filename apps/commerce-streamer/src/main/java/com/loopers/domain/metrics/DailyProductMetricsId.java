package com.loopers.domain.metrics;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * daily_product_metrics 의 복합 식별자 — (product_id, metric_date).
 * 같은 상품이라도 날짜마다 별개 행이므로 두 값이 함께 식별성을 이룬다.
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
