package com.loopers.application.coupon;

import com.loopers.domain.coupon.DiscountType;

import java.time.ZonedDateTime;

/**
 * 쿠폰 ADMIN 응용 계층 입력 DTO.
 * minOrderAmount 는 nullable — 없으면 0 으로 간주한다.
 */
public class CouponCommand {

    public record Create(
            String name,
            DiscountType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {}

    public record Update(
            String name,
            DiscountType type,
            long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt
    ) {}
}
