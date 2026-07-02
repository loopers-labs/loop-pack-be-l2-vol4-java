package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 집계 투영 저장소. 커스텀 쿼리 오케스트레이션이 없어(단순 findById/save) 도메인 인터페이스+Impl 분리 없이
 * Spring Data 를 직접 쓴다 — 얇은 위임만 하는 인터페이스는 불필요한 우회이므로.
 */
public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {
}
