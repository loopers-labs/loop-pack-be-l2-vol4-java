package com.loopers.domain.coupon;

import com.loopers.domain.outbox.OutboxableEvent;

// partition key(aggregateId)는 couponTemplateId다 - 같은 쿠폰의 발급 요청이 항상 같은 파티션에 몰려
// consumer가 순서대로 처리한다(선착순 판정의 전제). eventId는 requestId를 그대로 재사용한다 -
// 요청 자체가 이미 유일한 식별자라 별도 UUID를 추가로 발급할 필요가 없다.
public record CouponIssueRequestedEvent(String requestId, Long couponTemplateId, Long userId) implements OutboxableEvent {

    public static CouponIssueRequestedEvent from(CouponIssueRequestModel request) {
        return new CouponIssueRequestedEvent(request.getRequestId(), request.getCouponTemplateId(), request.getUserId());
    }

    @Override
    public String eventId() {
        return requestId;
    }

    @Override
    public String aggregateType() {
        return "CouponIssueRequest";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(couponTemplateId);
    }

    @Override
    public String eventType() {
        return "COUPON_ISSUE_REQUESTED";
    }
}
