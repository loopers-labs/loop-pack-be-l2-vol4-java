package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record CouponValue(long value) {

    public CouponValue {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 정책 값은 0보다 커야 합니다.");
        }
    }

    public static CouponValue of(long value) {
        return new CouponValue(value);
    }
}
