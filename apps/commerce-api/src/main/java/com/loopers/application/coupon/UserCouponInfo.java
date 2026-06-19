package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

public record UserCouponInfo(
    Long id,
    Long userId,
    CouponInfo coupon,
    UserCouponStatus status,
    ZonedDateTime createdAt
) {
    public static UserCouponInfo from(UserCouponModel model) {
        return new UserCouponInfo(
            model.getId(),
            model.getUserId(),
            CouponInfo.from(model.getCoupon()),
            model.computedStatus(),
            model.getCreatedAt()
        );
    }
}
