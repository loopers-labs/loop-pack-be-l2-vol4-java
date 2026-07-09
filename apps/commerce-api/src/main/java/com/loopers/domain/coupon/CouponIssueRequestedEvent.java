package com.loopers.domain.coupon;

/**
 * 선착순 쿠폰 발급 요청이 접수(PENDING INSERT)됐을 때 발행되는 도메인 이벤트 (Slice 4, Step3).
 * 멱등 재요청(이미 접수된 요청)에는 발행되지 않는다 → 요청 1건당 정확히 1개의 메시지.
 *
 * <p>도메인은 Kafka/Outbox를 모른다. {@code ApplicationEventPublisher}로 발행하고, 커밋 직전 리스너
 * ({@code CouponIssueRequestEventOutboxListener})가 {@code coupon-issue-requests} outbox 적재로 이어받는다.
 * 컨슈머는 payload의 {@code requestId/userId/couponId}로 발급을 확정한다.
 *
 * @param requestId 발급 요청 식별자(결과 조회 키)
 * @param userId    요청자
 * @param couponId  대상 쿠폰(파티션 키 — 같은 쿠폰 요청을 같은 파티션으로 직렬화)
 */
public record CouponIssueRequestedEvent(Long requestId, Long userId, Long couponId) {

    public static CouponIssueRequestedEvent from(CouponIssueRequestModel request) {
        return new CouponIssueRequestedEvent(request.getId(), request.getUserId(), request.getCouponId());
    }
}
