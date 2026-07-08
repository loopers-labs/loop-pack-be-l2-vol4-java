package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsJpaEntity, Long> {
    Optional<ProductMetricsJpaEntity> findByProductId(Long productId);
}
