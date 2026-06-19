package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;

import java.time.ZonedDateTime;

public final class CouponInfo {

    private CouponInfo() {}

    public record Template(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        public static Template from(Coupon coupon) {
            return new Template(
                coupon.getId(),
                coupon.getName(),
                coupon.getType(),
                coupon.getValue(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt()
            );
        }
    }

    public record Issued(
        Long id,
        Long couponId,
        String userLoginId,
        CouponStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        public static Issued from(IssuedCoupon issuedCoupon, ZonedDateTime now) {
            return new Issued(
                issuedCoupon.getId(),
                issuedCoupon.getCouponId(),
                issuedCoupon.getUserLoginId(),
                issuedCoupon.currentStatus(now),
                issuedCoupon.getExpiredAt(),
                issuedCoupon.getUsedAt()
            );
        }
    }
}
