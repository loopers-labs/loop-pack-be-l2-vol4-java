package com.loopers.batch.job.productranking;

import java.time.LocalDate;

public record ProductMetricItem(
    LocalDate metricDate, long productId, long viewCount, long likeCount, long orderCount) {}
