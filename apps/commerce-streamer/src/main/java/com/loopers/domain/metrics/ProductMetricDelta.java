package com.loopers.domain.metrics;

public record ProductMetricDelta(
    long viewCount, long likeCount, long orderCount, long orderQuantity, long orderAmount) {
  public static ProductMetricDelta viewed() {
    return new ProductMetricDelta(1L, 0L, 0L, 0L, 0L);
  }

  public static ProductMetricDelta liked() {
    return new ProductMetricDelta(0L, 1L, 0L, 0L, 0L);
  }

  public static ProductMetricDelta ordered(long quantity, long amount) {
    return new ProductMetricDelta(0L, 0L, 1L, quantity, amount);
  }
}
