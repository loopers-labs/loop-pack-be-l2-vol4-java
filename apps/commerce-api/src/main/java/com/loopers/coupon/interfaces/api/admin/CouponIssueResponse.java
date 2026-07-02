package com.loopers.coupon.interfaces.api.admin;

import com.loopers.coupon.application.MemberCouponInfo;
import com.loopers.coupon.domain.CouponState;

import java.time.ZonedDateTime;

public record CouponIssueResponse(
    Long memberCouponId,
    Long couponId,
    Long memberId,
    CouponState state,
    ZonedDateTime issuedAt,
    ZonedDateTime usedAt,
    ZonedDateTime expiredAt) {

    public static CouponIssueResponse from(MemberCouponInfo info) {
        return new CouponIssueResponse(
            info.memberCouponId(),
            info.couponId(),
            info.memberId(),
            info.state(),
            info.issuedAt(),
            info.usedAt(),
            info.expiredAt());
    }
}
