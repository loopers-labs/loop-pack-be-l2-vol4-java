package com.loopers.domain.coupon;

/**
 * "선착순 쿠폰 발급이 요청되었다"는 사실을 통지하는 이벤트.
 * Outbox 를 거쳐 coupon-issue-requests 토픽으로 발행되고(key=couponId → 같은 쿠폰은 순차 처리),
 * Consumer 가 수량 확인·중복 방지 후 발급을 수행한다.
 */
public record CouponIssueRequestedEvent(String requestId, Long couponId, Long userId) {
    public static CouponIssueRequestedEvent from(CouponIssueRequest request) {
        return new CouponIssueRequestedEvent(request.getRequestId(), request.getCouponId(), request.getUserId());
    }
}
