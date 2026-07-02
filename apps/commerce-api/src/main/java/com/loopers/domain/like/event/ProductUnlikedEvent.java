package com.loopers.domain.like.event;

import java.time.ZonedDateTime;

/**
 * 좋아요가 취소된 사실(이미 커밋됨)을 알리는 도메인 이벤트. 실제 취소(affected==1)된 경우에만 발행한다.
 */
public record ProductUnlikedEvent(Long userId, Long productId, ZonedDateTime occurredAt) {
}
