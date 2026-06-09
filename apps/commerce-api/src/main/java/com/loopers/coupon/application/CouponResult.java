package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;

public class CouponResult {

    public record Detail(
            Long id,
            String name,
            CouponType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
        public static Detail from(Coupon coupon) {
            return new Detail(
                    coupon.getId(),
                    coupon.getName(),
                    coupon.getType(),
                    coupon.getValue(),
                    coupon.getMinOrderAmount(),
                    coupon.getExpiredAt()
            );
        }
    }
}
