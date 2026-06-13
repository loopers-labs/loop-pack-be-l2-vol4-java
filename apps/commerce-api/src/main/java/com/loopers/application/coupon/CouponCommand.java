package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class CouponCommand {

    public record Create(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt) {}

    public record Update(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt) {}
}
