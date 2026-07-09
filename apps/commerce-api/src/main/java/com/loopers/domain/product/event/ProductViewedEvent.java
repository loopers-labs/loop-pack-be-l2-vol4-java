package com.loopers.domain.product.event;

import java.time.ZonedDateTime;

/**
 * 상품 상세가 조회된 사실을 알리는 도메인 이벤트.
 *
 * <p>조회는 읽기 경로(SUPPORTS·캐시 히트)라 트랜잭션이 없을 수 있다 → 이 이벤트를 듣는 리스너는
 * {@code fallbackExecution=true} 로 트랜잭션이 없으면 즉시 실행돼야 한다. 비로그인 조회가 가능하므로
 * {@code userId} 는 null 일 수 있다(익명 조회 추적).</p>
 */
public record ProductViewedEvent(Long productId, Long userId, ZonedDateTime occurredAt) {
}
