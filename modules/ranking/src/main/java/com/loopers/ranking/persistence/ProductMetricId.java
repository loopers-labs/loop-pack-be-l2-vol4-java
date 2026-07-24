package com.loopers.ranking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class ProductMetricId implements Serializable {

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    public ProductMetricId(LocalDate metricDate, Long productId) {
        this.metricDate = metricDate;
        this.productId = productId;
    }
}
