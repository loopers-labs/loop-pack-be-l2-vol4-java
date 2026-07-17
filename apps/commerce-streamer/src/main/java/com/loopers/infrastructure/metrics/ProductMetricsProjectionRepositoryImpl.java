package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricDelta;
import com.loopers.domain.metrics.ProductMetricsProjectionRepository;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductMetricsProjectionRepositoryImpl implements ProductMetricsProjectionRepository {

  private final ProductMetricJpaRepository productMetricJpaRepository;
  private final ProductMetricsEventInboxJpaRepository eventInboxJpaRepository;

  @Override
  public boolean markProcessed(UUID eventId) {
    return eventInboxJpaRepository.insertIgnore(eventId.toString()) == 1;
  }

  @Override
  public void increment(LocalDate metricDate, Long productId, ProductMetricDelta delta) {
    productMetricJpaRepository.increment(
        metricDate,
        productId,
        delta.viewCount(),
        delta.likeCount(),
        delta.orderCount(),
        delta.orderQuantity(),
        delta.orderAmount());
  }
}
