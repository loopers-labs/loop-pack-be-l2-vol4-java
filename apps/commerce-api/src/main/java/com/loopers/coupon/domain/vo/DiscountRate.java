package com.loopers.coupon.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record DiscountRate(long value) {

    public DiscountRate {
        if (value < 1 || value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰 비율은 1 이상 100 이하이어야 합니다.");
        }
    }

    public static DiscountRate of(DiscountValue discountValue) {
        if (discountValue == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰 값은 비어있을 수 없습니다.");
        }
        return new DiscountRate(discountValue.value());
    }

    public CouponMoney discount(CouponMoney orderAmount) {
        if (orderAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 비어있을 수 없습니다.");
        }
        return CouponMoney.of(orderAmount.value() * value / 100);
    }
}
