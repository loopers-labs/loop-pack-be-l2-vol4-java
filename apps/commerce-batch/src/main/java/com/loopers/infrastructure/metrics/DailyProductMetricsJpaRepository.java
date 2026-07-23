package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyProductMetricsJpaRepository extends JpaRepository<DailyProductMetrics, DailyProductMetricsId> {
}
