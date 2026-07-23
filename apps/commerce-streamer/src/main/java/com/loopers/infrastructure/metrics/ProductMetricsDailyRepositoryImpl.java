package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsDailyModel;
import com.loopers.domain.metrics.ProductMetricsDailyRepository;
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
    public void increaseLikeCount(Long productId, LocalDate metricDate) {
        productMetricsDailyJpaRepository.increaseLikeCount(productId, metricDate);
    }

    @Override
    public void decreaseLikeCount(Long productId, LocalDate metricDate) {
        productMetricsDailyJpaRepository.decreaseLikeCount(productId, metricDate);
    }

    @Override
    public void increaseOrderCount(Long productId, LocalDate metricDate, Long quantity) {
        productMetricsDailyJpaRepository.increaseOrderCount(productId, metricDate, quantity);
    }

    @Override
    public void increaseViewCount(Long productId, LocalDate metricDate) {
        productMetricsDailyJpaRepository.increaseViewCount(productId, metricDate);
    }
}
