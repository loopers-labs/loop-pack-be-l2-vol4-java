package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetricsDailyModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsDailyJpaRepository extends JpaRepository<ProductMetricsDailyModel, Long> {
    Optional<ProductMetricsDailyModel> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);
}
