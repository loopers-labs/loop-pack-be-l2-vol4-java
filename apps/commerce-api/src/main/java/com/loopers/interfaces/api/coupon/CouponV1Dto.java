package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CouponV1Dto {

    public record IssueResponse(
        Long id,
        Long couponId,
        CouponStatus status
    ) {
        public static IssueResponse from(IssuedCouponInfo info) {
            return new IssueResponse(info.id(), info.couponId(), info.status());
        }
    }

    public record MyCouponResponse(
        Long id,
        Long couponId,
        String name,
        CouponType type,
        long value,
        BigDecimal minOrderAmount,
        LocalDateTime expiredAt,
        CouponStatus status
    ) {
        public static MyCouponResponse from(UserCouponInfo info) {
            return new MyCouponResponse(
                info.id(),
                info.couponId(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.status()
            );
        }
    }
}
