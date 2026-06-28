package com.loopers.application.activity;

public record UserActivityEvent(Long userId, Type type, Long targetId) {

    public static UserActivityEvent of(Long userId, Type type, Long targetId) {
        return new UserActivityEvent(userId, type, targetId);
    }

    public enum Type {
        PRODUCT_LIKED,
        PRODUCT_UNLIKED,
        ORDER_PLACED,
    }
}
