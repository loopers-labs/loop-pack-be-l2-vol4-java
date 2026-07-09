package com.loopers.domain.like.event;

public record ProductLikedEvent(Long userId, Long productId) {
}
