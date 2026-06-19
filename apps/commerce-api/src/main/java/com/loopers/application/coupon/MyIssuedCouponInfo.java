package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponModel;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record MyIssuedCouponInfo(
        Long id,
        String name,
        CouponType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        ZonedDateTime expiredAt,
        CouponStatus status
) {
    public static MyIssuedCouponInfo from(IssuedCouponModel issued, CouponTemplateModel template) {
        return new MyIssuedCouponInfo(
                issued.getId(),
                template.getName(),
                template.getDiscountPolicy().type(),
                template.getDiscountPolicy().value(),
                template.getMinOrderAmount(),
                template.getExpiredAt(),
                issued.getStatus()
        );
    }
}
