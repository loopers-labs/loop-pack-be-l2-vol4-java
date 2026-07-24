package com.loopers.metrics.application;

import java.time.Instant;
import java.util.List;

public interface ProductMetricsRepository {

    void addAll(List<ProductMetricDelta> deltas, Instant updatedAt);
}
