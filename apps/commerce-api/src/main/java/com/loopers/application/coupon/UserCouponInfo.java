package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.coupon.vo.CouponMoney;

import java.time.ZonedDateTime;

public record UserCouponInfo(
    Long id,
    Long userId,
    Long couponTemplateId,
    String name,
    CouponType type,
    long discountValue,
    Long minimumOrderAmount,
    ZonedDateTime expiredAt,
    UserCouponStatus status,
    ZonedDateTime issuedAt,
    ZonedDateTime usedAt
) {

    public static UserCouponInfo from(UserCoupon userCoupon, CouponTemplate couponTemplate) {
        return new UserCouponInfo(
            userCoupon.getId(),
            userCoupon.getUserId(),
            couponTemplate.getId(),
            couponTemplate.getName(),
            couponTemplate.getType(),
            couponTemplate.getDiscountValue().value(),
            minimumOrderAmount(couponTemplate),
            couponTemplate.getExpiration().expiredAt(),
            userCoupon.getStatus(),
            userCoupon.getCreatedAt(),
            userCoupon.getUsedAt()
        );
    }

    private static Long minimumOrderAmount(CouponTemplate couponTemplate) {
        CouponMoney minimumOrderAmount = couponTemplate.getMinimumOrderAmount();
        return minimumOrderAmount == null ? null : minimumOrderAmount.value();
    }
}
