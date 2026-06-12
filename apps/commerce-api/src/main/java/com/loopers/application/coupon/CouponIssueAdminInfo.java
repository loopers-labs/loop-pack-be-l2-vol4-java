package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCouponModel;

import java.time.ZonedDateTime;

public record CouponIssueAdminInfo(
    Long id,
    Long userId,
    Long couponId,
    CouponStatus status,
    ZonedDateTime issuedAt
) {
    public static CouponIssueAdminInfo from(UserCouponModel userCoupon) {
        return new CouponIssueAdminInfo(
            userCoupon.getId(),
            userCoupon.getUserId(),
            userCoupon.getCouponId(),
            userCoupon.getStatus(),
            userCoupon.getCreatedAt()
        );
    }
}
