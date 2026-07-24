package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetricsHourly;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 배치 측 product_metrics_hourly 접근. 집계 자체는 JpaPagingItemReader의 JPQL이 담당하고,
 * 이 리포지토리는 매핑 등록과 (테스트) 시드 적재에 쓴다.
 */
public interface ProductMetricsHourlyJpaRepository extends JpaRepository<ProductMetricsHourly, Long> {
}
