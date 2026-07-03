package com.loopers.domain.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    // @Modifying UPDATE는 활성 트랜잭션이 필수 - MetricsFacade의 트랜잭션에 합류하도록 추가
    @Transactional
    public void increaseLikeCount(Long productId) {
        productMetricsRepository.increaseLikeCount(productId);
    }

    @Transactional
    public void decreaseLikeCount(Long productId) {
        productMetricsRepository.decreaseLikeCount(productId);
    }

    @Transactional
    public void increaseSalesCount(Long productId, Long quantity) {
        productMetricsRepository.increaseSalesCount(productId, quantity);
    }

    @Transactional
    public void increaseViewCount(Long productId) {
        productMetricsRepository.increaseViewCount(productId);
    }
}
