package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductMetricSummaryJpaRepository extends JpaRepository<ProductMetricSummaryJpaEntity, String> {
    List<ProductMetricSummaryJpaEntity> findAllByProductIdIn(List<String> productIds);
}
