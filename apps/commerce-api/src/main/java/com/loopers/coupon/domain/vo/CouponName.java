package com.loopers.coupon.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record CouponName(String value) {

    public CouponName {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
    }

    public static CouponName of(String value) {
        return new CouponName(value);
    }
}
