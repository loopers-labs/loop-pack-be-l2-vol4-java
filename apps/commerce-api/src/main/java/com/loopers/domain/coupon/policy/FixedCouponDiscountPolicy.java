package com.loopers.domain.coupon.policy;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.domain.coupon.vo.CouponValue;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class FixedCouponDiscountPolicy implements CouponDiscountPolicy {

    @Override
    public CouponType type() {
        return CouponType.FIXED;
    }

    @Override
    public void validateValue(CouponValue value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정액 쿠폰 값은 비어있을 수 없습니다.");
        }
    }

    @Override
    public CouponMoney calculateDiscount(CouponMoney orderAmount, CouponValue value) {
        return orderAmount.min(CouponMoney.of(value.value()));
    }
}
