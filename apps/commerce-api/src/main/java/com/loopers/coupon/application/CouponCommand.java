package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponType;

import java.time.ZonedDateTime;

public class CouponCommand {

    public record Create(
            String name,
            CouponType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt,
            Long quantity
    ) {
        // 수량 무제한(quantity=null) 생성용 — 기존 호출부 호환
        public Create(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
            this(name, type, value, minOrderAmount, expiredAt, null);
        }
    }

    public record Update(
            Long couponId,
            String name,
            CouponType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {
    }
}
