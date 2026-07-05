package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponPolicy;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

/**
 * coupon-issue-requests 토픽으로 발행되는 ECST(Event-Carried State Transfer) 페이로드.
 * 컨슈머(streamer)가 coupon_policy 를 다시 읽지 않고 UserCoupon 을 구성할 수 있도록,
 * 발급요청 시점의 정책 스냅샷(type/discountValue/minOrderAmount/expiredAt)을 함께 싣는다.
 * requestId 로 컨슈머가 처리 결과(ISSUED/REJECTED)를 해당 요청에 되돌려 기록한다.
 */
public record CouponIssueRequestMessage(
    Long requestId,
    Long userId,
    Long couponPolicyId,
    CouponType type,
    long discountValue,
    Long minOrderAmount,
    ZonedDateTime expiredAt
) {
    public static CouponIssueRequestMessage of(CouponIssueRequest request, CouponPolicy policy) {
        return new CouponIssueRequestMessage(
            request.getId(),
            request.getUserId(),
            request.getCouponPolicyId(),
            policy.getType(),
            policy.getValue(),
            policy.getMinOrderAmount(),
            policy.getExpiredAt()
        );
    }
}
