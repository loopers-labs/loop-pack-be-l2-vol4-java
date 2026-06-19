package com.loopers.coupon.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
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

    public CouponMoney add(CouponMoney amount) {
        return CouponMoney.of(value + requireAmount(amount).value);
    }

    public CouponMoney minus(CouponMoney amount) {
        return CouponMoney.of(value - requireAmount(amount).value);
    }

    public CouponMoney min(CouponMoney amount) {
        return value <= requireAmount(amount).value ? this : amount;
    }

    public boolean isLessThan(CouponMoney amount) {
        return value < requireAmount(amount).value;
    }

    private static CouponMoney requireAmount(CouponMoney amount) {
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비교할 쿠폰 금액은 비어있을 수 없습니다.");
        }
        return amount;
    }
}
