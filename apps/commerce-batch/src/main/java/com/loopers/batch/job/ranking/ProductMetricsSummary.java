package com.loopers.batch.job.ranking;

import java.util.UUID;

public record ProductMetricsSummary(
    UUID productId,
    long likeCount,
    long viewCount,
    long salesCount
) {}
