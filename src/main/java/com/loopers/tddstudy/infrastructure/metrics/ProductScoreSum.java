package com.loopers.tddstudy.infrastructure.metrics;

public record ProductScoreSum(
        Long productId,
        long likeSum,
        long salesSum,
        long viewSum,
        double score
) {}
