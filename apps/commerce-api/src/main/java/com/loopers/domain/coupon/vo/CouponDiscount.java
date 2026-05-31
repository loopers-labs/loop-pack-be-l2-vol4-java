package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record CouponDiscount(CouponMoney originalAmount, CouponMoney discountAmount, CouponMoney finalAmount) {

    public CouponDiscount {
        if (originalAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 적용 전 금액은 비어있을 수 없습니다.");
        }
        if (discountAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 금액은 비어있을 수 없습니다.");
        }
        if (finalAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 적용 후 금액은 비어있을 수 없습니다.");
        }
        if (!originalAmount.equals(discountAmount.add(finalAmount))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 금액 계산 결과가 올바르지 않습니다.");
        }
    }

    public static CouponDiscount of(CouponMoney originalAmount, CouponMoney discountAmount) {
        return new CouponDiscount(originalAmount, discountAmount, originalAmount.minus(discountAmount));
    }
}
