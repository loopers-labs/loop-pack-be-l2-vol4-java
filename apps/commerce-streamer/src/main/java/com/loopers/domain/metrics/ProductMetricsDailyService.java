package com.loopers.domain.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class ProductMetricsDailyService {

    private final ProductMetricsDailyRepository productMetricsDailyRepository;

    // @Modifying UPSERT는 활성 트랜잭션이 필수 - MetricsFacade의 트랜잭션에 합류하도록 @Transactional을 둔다.
    @Transactional
    public void increaseLikeCount(Long productId, LocalDate metricDate) {
        productMetricsDailyRepository.increaseLikeCount(productId, metricDate);
    }

    @Transactional
    public void decreaseLikeCount(Long productId, LocalDate metricDate) {
        productMetricsDailyRepository.decreaseLikeCount(productId, metricDate);
    }

    @Transactional
    public void increaseOrderCount(Long productId, LocalDate metricDate, Long quantity) {
        productMetricsDailyRepository.increaseOrderCount(productId, metricDate, quantity);
    }

    @Transactional
    public void increaseViewCount(Long productId, LocalDate metricDate) {
        productMetricsDailyRepository.increaseViewCount(productId, metricDate);
    }
}
