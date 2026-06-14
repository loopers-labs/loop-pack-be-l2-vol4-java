package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.Discount;
import com.loopers.domain.coupon.UserCoupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserCouponInfo(
    Long id,
    Long couponId,
    String name,
    CouponType type,
    long value,
    BigDecimal minOrderAmount,
    LocalDateTime expiredAt,
    CouponStatus status
) {
    public static UserCouponInfo of(UserCoupon userCoupon, Coupon coupon, CouponStatus displayStatus) {
        Discount discount = coupon.getDiscount();
        return new UserCouponInfo(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            discount.getType(),
            discount.getValue(),
            coupon.getMinOrderAmount().getAmount(),
            coupon.getExpiredAt(),
            displayStatus
        );
    }
}
