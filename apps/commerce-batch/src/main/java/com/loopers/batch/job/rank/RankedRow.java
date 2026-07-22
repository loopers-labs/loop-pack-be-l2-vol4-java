package com.loopers.batch.job.rank;

public record RankedRow(int rankNo, Long productId, double score) {}