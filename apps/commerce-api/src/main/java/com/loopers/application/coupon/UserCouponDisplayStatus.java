package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.coupon.vo.CouponExpiration;

import java.time.ZonedDateTime;

public record UserCouponDisplayStatus(
    UserCouponStatus storedStatus,
    CouponExpiration expiration
) {

    public static UserCouponDisplayStatus fromUserCouponStatus(UserCouponStatus storedStatus, ZonedDateTime expiredAt) {
        return new UserCouponDisplayStatus(storedStatus, CouponExpiration.of(expiredAt));
    }

    public UserCouponStatus toDisplayStatus(ZonedDateTime now) {
        if (storedStatus == UserCouponStatus.AVAILABLE && expiration.isExpiredAt(now)) {
            return UserCouponStatus.EXPIRED;
        }
        return storedStatus;
    }
}
