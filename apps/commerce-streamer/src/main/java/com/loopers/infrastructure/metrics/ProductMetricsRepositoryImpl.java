package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public void applyLike(Long productId, LocalDate date, long likeCount, long version, int likeDelta) {
        jpaRepository.applyLike(productId, date, likeCount, version, likeDelta);
    }

    @Override
    public void addSales(Long productId, LocalDate date, int quantity) {
        jpaRepository.addSales(productId, date, quantity);
    }

    @Override
    public void addView(Long productId, LocalDate date) {
        jpaRepository.addView(productId, date);
    }

    @Override
    public Optional<ProductMetrics> find(Long productId, LocalDate date) {
        return jpaRepository.findByProductIdAndMetricDate(productId, date);
    }
}
