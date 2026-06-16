package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponIssue;
import com.loopers.domain.coupon.UserCouponModel;

import java.time.ZonedDateTime;
import java.util.List;

public record UserCouponInfo(
        Long id,
        Long userId,
        CouponInfo coupon,
        String status,
        ZonedDateTime usedAt
) {
    public static UserCouponInfo from(UserCouponModel userCoupon) {
        return new UserCouponInfo(
                userCoupon.getId(),
                userCoupon.getUserId(),
                CouponInfo.from(userCoupon.getCoupon()),
                userCoupon.getStatus().getDescription(),
                userCoupon.getUsedAt()
        );
    }

    public static UserCouponInfo from(UserCouponIssue issue) {
        return new UserCouponInfo(
                issue.id(),
                issue.userId(),
                new CouponInfo(issue.couponId(), null, null, null, null, null),
                issue.status().getDescription(),
                issue.usedAt()
        );
    }

    public static List<UserCouponInfo> from(List<UserCouponModel> userCoupons) {
        return userCoupons.stream().map(UserCouponInfo::from).toList();
    }
}
