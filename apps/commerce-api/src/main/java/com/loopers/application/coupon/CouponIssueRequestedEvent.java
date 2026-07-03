package com.loopers.application.coupon;

/**
 * 선착순 쿠폰 "발급 요청" 애플리케이션 이벤트. requestIssue 트랜잭션 안에서 발행되어
 * OutboxEventListener(BEFORE_COMMIT)가 outbox 행으로 함께 커밋하고, Relay가 Kafka로 발행한다.
 * 실제 발급(수량 제한)은 컨슈머가 수행한다.
 *
 * @param requestId 요청 추적용 ID (결과 확인·outbox eventId로 사용)
 * @param couponId  발급 대상 쿠폰 (파티션 키로도 사용)
 * @param userId    발급 요청 유저
 */
public record CouponIssueRequestedEvent(
    String requestId,
    Long couponId,
    Long userId
) {
}
