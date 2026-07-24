package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {
    Optional<ProductMetrics> findByMetricDateAndProductId(LocalDate metricDate, Long productId);
}
