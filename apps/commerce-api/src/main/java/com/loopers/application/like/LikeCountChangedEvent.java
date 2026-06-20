package com.loopers.application.like;

public record LikeCountChangedEvent(Long productId, boolean increase) {
}