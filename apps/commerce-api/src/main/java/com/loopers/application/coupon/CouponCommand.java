package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

public class CouponCommand {

    public record Create(String name, String type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        public Coupon toCoupon() {
            return new Coupon(name, CouponType.from(type), value, minOrderAmount, expiredAt);
        }
    }

    public record Update(String name, String type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {}
}
