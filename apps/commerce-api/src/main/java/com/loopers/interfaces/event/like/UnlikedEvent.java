package com.loopers.interfaces.event.like;

public record UnlikedEvent(
    Long memberId,
    Long productId
) {}
