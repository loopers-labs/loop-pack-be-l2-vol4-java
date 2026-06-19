package com.loopers.coupon.application;

import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponStatus;

import java.time.ZonedDateTime;

public record CouponIssueInfo(
    Long userCouponId,
    Long userId,
    UserCouponStatus status,
    ZonedDateTime issuedAt,
    ZonedDateTime usedAt
) {

    public static CouponIssueInfo from(UserCoupon userCoupon, ZonedDateTime now) {
        UserCouponStatus displayStatus = UserCouponDisplayStatus
            .fromUserCouponStatus(userCoupon.getStatus(), userCoupon.getExpiration().expiredAt())
            .toDisplayStatus(now);
        return new CouponIssueInfo(
            userCoupon.getId(),
            userCoupon.getUserId(),
            displayStatus,
            userCoupon.getCreatedAt(),
            userCoupon.getUsedAt()
        );
    }
}
