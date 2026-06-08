package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;

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
