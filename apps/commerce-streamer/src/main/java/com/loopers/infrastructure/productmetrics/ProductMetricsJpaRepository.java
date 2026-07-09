package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {
    Optional<ProductMetricsModel> findByProductId(Long productId);
}
