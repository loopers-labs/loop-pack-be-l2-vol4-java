package com.loopers.coupon.domain.policy;

import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.vo.CouponMoney;
import com.loopers.coupon.domain.vo.DiscountValue;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class FixedCouponDiscountPolicy implements CouponDiscountPolicy {

    @Override
    public CouponType type() {
        return CouponType.FIXED;
    }

    @Override
    public void confirmDiscountValue(DiscountValue discountValue) {
        if (discountValue == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정액 쿠폰 값은 비어있을 수 없습니다.");
        }
    }

    @Override
    public CouponMoney discount(CouponMoney orderAmount, DiscountValue discountValue) {
        return orderAmount.min(CouponMoney.of(discountValue.value()));
    }
}
