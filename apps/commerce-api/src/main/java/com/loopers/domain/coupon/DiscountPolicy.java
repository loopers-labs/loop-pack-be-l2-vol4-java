package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record DiscountPolicy(
        @Enumerated(EnumType.STRING) CouponType type,
        Long value
) {

    public DiscountPolicy {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (value == null || value <= 0L) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && value > 100L) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 100을 넘을 수 없습니다.");
        }
    }

    public static DiscountPolicy of(CouponType type, Long value) {
        return new DiscountPolicy(type, value);
    }

    public long discountFor(long orderAmount) {
        return type.discount(value, orderAmount);
    }
}