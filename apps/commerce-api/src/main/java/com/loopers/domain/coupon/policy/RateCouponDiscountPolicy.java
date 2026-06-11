package com.loopers.domain.coupon.policy;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.domain.coupon.vo.DiscountValue;
import com.loopers.domain.coupon.vo.DiscountRate;
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
