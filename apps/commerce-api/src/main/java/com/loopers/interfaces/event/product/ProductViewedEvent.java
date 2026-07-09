package com.loopers.interfaces.event.product;

public record ProductViewedEvent(
    Long productId,
    Long memberId
) {}
