package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.UUID;

public interface ProductMetricsProjectionRepository {

  boolean markProcessed(UUID eventId);

  void increment(LocalDate metricDate, Long productId, ProductMetricDelta delta);
}
