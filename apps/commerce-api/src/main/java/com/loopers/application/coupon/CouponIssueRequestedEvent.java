package com.loopers.application.coupon;

/**
 * 선착순 쿠폰 발급 요청 접수 이벤트.
 *
 * <p>접수 트랜잭션(요청 행 INSERT) 안에서 발행되고, {@code OutboxEventListener}(BEFORE_COMMIT)가
 * outbox 에 기록한다 — 접수 행과 outbox 행이 원자적으로 커밋되어 "202 를 받은 요청은 반드시 처리된다".
 *
 * <p>{@code requestId} 가 Kafka 메시지의 eventId 로 쓰인다(요청 1건 = 메시지 1건).
 */
public record CouponIssueRequestedEvent(String requestId, Long userId, Long couponId) {
}
