package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record CouponMoney(long value) {

    public CouponMoney {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 금액은 0 이상이어야 합니다.");
        }
    }

    public static CouponMoney of(long value) {
        return new CouponMoney(value);
    }

    public CouponMoney add(CouponMoney other) {
        return CouponMoney.of(value + requireOther(other).value);
    }

    public CouponMoney minus(CouponMoney other) {
        return CouponMoney.of(value - requireOther(other).value);
    }

    public CouponMoney min(CouponMoney other) {
        return value <= requireOther(other).value ? this : other;
    }

    public boolean isLessThan(CouponMoney other) {
        return value < requireOther(other).value;
    }

    private static CouponMoney requireOther(CouponMoney other) {
        if (other == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비교할 쿠폰 금액은 비어있을 수 없습니다.");
        }
        return other;
    }
}
