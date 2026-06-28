package com.loopers.domain.like.event;

public record LikeChangedEvent(Long productId, Type type) {

    public enum Type { LIKED, UNLIKED }

    public static LikeChangedEvent liked(Long productId) {
        return new LikeChangedEvent(productId, Type.LIKED);
    }

    public static LikeChangedEvent unliked(Long productId) {
        return new LikeChangedEvent(productId, Type.UNLIKED);
    }
}
