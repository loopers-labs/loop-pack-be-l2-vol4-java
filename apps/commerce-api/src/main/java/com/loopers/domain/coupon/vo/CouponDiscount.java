package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record CouponDiscount(CouponMoney orderAmount, CouponMoney discountAmount, CouponMoney paymentAmount) {

    public CouponDiscount {
        if (orderAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 적용 전 금액은 비어있을 수 없습니다.");
        }
        if (discountAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 금액은 비어있을 수 없습니다.");
        }
        if (paymentAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 적용 후 금액은 비어있을 수 없습니다.");
        }
        if (!orderAmount.equals(discountAmount.add(paymentAmount))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 금액 계산 결과가 올바르지 않습니다.");
        }
    }

    public static CouponDiscount of(CouponMoney orderAmount, CouponMoney discountAmount) {
        return new CouponDiscount(orderAmount, discountAmount, orderAmount.minus(discountAmount));
    }

    public static CouponDiscount none(CouponMoney orderAmount) {
        return of(orderAmount, CouponMoney.of(0L));
    }
}
