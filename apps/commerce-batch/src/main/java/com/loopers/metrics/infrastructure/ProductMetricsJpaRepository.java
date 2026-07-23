package com.loopers.metrics.infrastructure;

import com.loopers.metrics.domain.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {
}
