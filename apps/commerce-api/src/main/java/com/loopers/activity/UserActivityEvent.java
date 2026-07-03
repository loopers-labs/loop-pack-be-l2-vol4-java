package com.loopers.activity;

import java.time.ZonedDateTime;

/**
 * 유저 행동(조회/좋아요/주문 등)을 알리는 이벤트. 본 흐름과 분리해 서버 레벨 로깅으로만 처리한다.
 * 로깅 실패가 본 트랜잭션에 영향을 주지 않도록 부가 로직으로 떼어낸다.
 */
public record UserActivityEvent(
        Long userId,
        UserAction action,
        String targetType,
        Long targetId,
        ZonedDateTime occurredAt
) {
    public static UserActivityEvent of(Long userId, UserAction action, String targetType, Long targetId) {
        return new UserActivityEvent(userId, action, targetType, targetId, ZonedDateTime.now());
    }

    public static UserActivityEvent like(Long userId, Long productId) {
        return of(userId, UserAction.LIKE, "PRODUCT", productId);
    }

    public static UserActivityEvent unlike(Long userId, Long productId) {
        return of(userId, UserAction.UNLIKE, "PRODUCT", productId);
    }

    public static UserActivityEvent order(Long userId, Long orderId) {
        return of(userId, UserAction.ORDER, "ORDER", orderId);
    }
}
