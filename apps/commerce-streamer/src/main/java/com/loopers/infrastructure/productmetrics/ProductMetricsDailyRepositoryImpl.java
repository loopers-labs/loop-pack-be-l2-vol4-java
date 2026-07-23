package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetricsDailyModel;
import com.loopers.domain.productmetrics.ProductMetricsDailyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsDailyRepositoryImpl implements ProductMetricsDailyRepository {

    private final ProductMetricsDailyJpaRepository productMetricsDailyJpaRepository;

    @Override
    public Optional<ProductMetricsDailyModel> findByProductIdAndMetricDate(Long productId, LocalDate metricDate) {
        return productMetricsDailyJpaRepository.findByProductIdAndMetricDate(productId, metricDate);
    }

    @Override
    public ProductMetricsDailyModel save(ProductMetricsDailyModel productMetricsDaily) {
        return productMetricsDailyJpaRepository.save(productMetricsDaily);
    }
}
