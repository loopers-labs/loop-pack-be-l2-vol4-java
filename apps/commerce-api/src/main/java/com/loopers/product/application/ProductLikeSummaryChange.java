package com.loopers.product.application;

public record ProductLikeSummaryChange(
    Long productId,
    long changeAmount
) {}
