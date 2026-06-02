package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.coupon.vo.CouponMoney;

import java.time.ZonedDateTime;

public record UserCouponInfo(
    Long id,
    Long couponTemplateId,
    String name,
    CouponType type,
    long discountValue,
    Long minimumOrderAmount,
    ZonedDateTime expiredAt,
    UserCouponStatus displayStatus,
    ZonedDateTime issuedAt,
    ZonedDateTime usedAt
) {

    // 방금 발급된 쿠폰은 만료될 수 없으므로 저장 상태가 곧 표시 상태다.
    public static UserCouponInfo from(UserCoupon userCoupon, CouponTemplate couponTemplate) {
        return new UserCouponInfo(
            userCoupon.getId(),
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
