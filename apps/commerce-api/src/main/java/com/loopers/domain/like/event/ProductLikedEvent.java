package com.loopers.domain.like.event;

import java.time.ZonedDateTime;

/**
 * 좋아요가 새로 등록된 사실(이미 커밋됨)을 알리는 도메인 이벤트.
 *
 * <p>좋아요 쓰기는 주요(트랜잭션) 로직이고, 이 사실에서 파생되는 집계(likeCount)·행동 로깅·전파는
 * 부가 로직이라 이벤트로 분리한다. 좋아요가 실제 등록(affected==1)된 경우에만 발행한다.</p>
 */
public record ProductLikedEvent(Long userId, Long productId, ZonedDateTime occurredAt) {
}
