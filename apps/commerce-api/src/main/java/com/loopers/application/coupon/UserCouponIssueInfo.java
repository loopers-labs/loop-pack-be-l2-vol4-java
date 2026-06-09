package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponModel;

public record UserCouponIssueInfo(Long userCouponId) {

    public static UserCouponIssueInfo from(UserCouponModel userCoupon) {
        return new UserCouponIssueInfo(userCoupon.getId());
    }
}
