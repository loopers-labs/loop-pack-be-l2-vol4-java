package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;

import java.time.ZonedDateTime;

/** 쿠폰 템플릿(ADMIN)의 응용 계층 출력 DTO. */
public record CouponTemplateInfo(
        Long id,
        String name,
        DiscountType type,
        long value,
        long minOrderAmount,
        ZonedDateTime expiredAt
) {
    public static CouponTemplateInfo from(CouponModel coupon) {
        DiscountPolicy policy = coupon.getDiscountPolicy();
        return new CouponTemplateInfo(
                coupon.getId(),
                coupon.getName(),
                policy.getType(),
                policy.getValue(),
                policy.getMinOrderAmount().amount(),
                coupon.getExpiredAt()
        );
    }
}
