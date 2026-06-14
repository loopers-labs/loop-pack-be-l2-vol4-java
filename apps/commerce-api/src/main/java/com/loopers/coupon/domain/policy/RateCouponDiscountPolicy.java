package com.loopers.coupon.domain.policy;

import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.vo.CouponMoney;
import com.loopers.coupon.domain.vo.DiscountValue;
import com.loopers.coupon.domain.vo.DiscountRate;
import org.springframework.stereotype.Component;

@Component
public class RateCouponDiscountPolicy implements CouponDiscountPolicy {

    @Override
    public CouponType type() {
        return CouponType.RATE;
    }

    @Override
    public void confirmDiscountValue(DiscountValue discountValue) {
        DiscountRate.of(discountValue);
    }

    @Override
    public CouponMoney discount(CouponMoney orderAmount, DiscountValue discountValue) {
        return DiscountRate.of(discountValue).discount(orderAmount);
    }
}
