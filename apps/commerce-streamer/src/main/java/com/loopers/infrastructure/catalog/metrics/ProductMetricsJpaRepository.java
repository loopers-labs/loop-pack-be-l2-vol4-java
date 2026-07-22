package com.loopers.infrastructure.catalog.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsJpaEntity, Long> {
    Optional<ProductMetricsJpaEntity> findByMetricDateAndProductId(LocalDate metricDate, Long productId);
}
