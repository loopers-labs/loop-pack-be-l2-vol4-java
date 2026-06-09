package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

@Embeddable
public record DiscountPolicy(
        @Enumerated(EnumType.STRING) CouponType type,
        BigDecimal value
) {
    public DiscountPolicy {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입이 없습니다.");
        }
        if (type == CouponType.FIXED) {
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0보다 커야 합니다.");
            }
        }
        if (type == CouponType.RATE) {
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "할인율은 0보다 커야 합니다.");
            }
            if (value.compareTo(new BigDecimal("100")) > 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "할인율은 100을 초과할 수 없습니다.");
            }
        }
    }

}
