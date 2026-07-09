package com.loopers.domain.like;

import java.time.ZonedDateTime;
import java.util.UUID;

public record LikeChangedEvent(
        String eventId,
        Long userId,
        Long productId,
        Action action,
        ZonedDateTime occurredAt
) {
    public enum Action { LIKED, UNLIKED }

    public static LikeChangedEvent liked(Long userId, Long productId) {
        return new LikeChangedEvent(UUID.randomUUID().toString(), userId, productId, Action.LIKED, ZonedDateTime.now());
    }

    public static LikeChangedEvent unliked(Long userId, Long productId) {
        return new LikeChangedEvent(UUID.randomUUID().toString(), userId, productId, Action.UNLIKED, ZonedDateTime.now());
    }
}