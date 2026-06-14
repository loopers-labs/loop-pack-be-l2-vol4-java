package com.loopers.coupon.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record CouponDiscount(CouponMoney amount) {

    public CouponDiscount {
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 금액은 비어있을 수 없습니다.");
        }
    }

    public static CouponDiscount of(CouponMoney amount) {
        return new CouponDiscount(amount);
    }

    public static CouponDiscount none() {
        return new CouponDiscount(CouponMoney.of(0L));
    }

    public long value() {
        return amount.value();
    }
}
