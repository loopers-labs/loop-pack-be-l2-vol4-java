package com.loopers.application.coupon;

import java.time.ZonedDateTime;

/**
 * coupon-issue-requests 봉투. 프로듀서(commerce-api)의 OutboxMessage 와 같은 JSON 모양을 streamer 가 자기 DTO 로 받는다.
 * data 는 ECST 스냅샷 — 컨슈머가 coupon_policy 를 다시 읽지 않고 UserCoupon 을 구성하는 데 필요한 정책값을 모두 싣는다.
 * type 은 streamer 가 해석하지 않고 그대로 통과시키므로 String 으로 받는다.
 */
public record CouponIssueMessage(String eventId, String eventType, Long aggregateId, CouponIssueData data) {

    public record CouponIssueData(
        Long requestId,
        Long userId,
        Long couponPolicyId,
        String type,
        long discountValue,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
    }
}
