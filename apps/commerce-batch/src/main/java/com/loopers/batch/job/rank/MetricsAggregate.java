package com.loopers.batch.job.rank;

public record MetricsAggregate(Long productId, long viewSum, long likeSum, long salesSum) {}