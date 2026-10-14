package com.loopers.domain.event;

import java.time.ZonedDateTime;

/**
 * 좋아요 상태가 변경됐음. Consumer 는 product_metrics 의 like_count 를 delta 로 갱신한다.
 * <p>
 * action: LIKED / UNLIKED — 카운트 증가/감소 방향 결정.
 */
public record LikeChangedEvent(
    Long userId,
    Long productId,
    Action action,
    ZonedDateTime occurredAt
) {
    public enum Action { LIKED, UNLIKED }

    public static LikeChangedEvent liked(Long userId, Long productId) {
        return new LikeChangedEvent(userId, productId, Action.LIKED, ZonedDateTime.now());
    }

    public static LikeChangedEvent unliked(Long userId, Long productId) {
        return new LikeChangedEvent(userId, productId, Action.UNLIKED, ZonedDateTime.now());
    }
}
