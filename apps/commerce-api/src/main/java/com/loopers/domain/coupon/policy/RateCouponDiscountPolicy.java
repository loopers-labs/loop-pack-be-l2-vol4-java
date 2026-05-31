package com.loopers.domain.coupon.policy;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.domain.coupon.vo.CouponValue;
import com.loopers.domain.coupon.vo.DiscountRate;
import org.springframework.stereotype.Component;

@Component
public class RateCouponDiscountPolicy implements CouponDiscountPolicy {

    @Override
    public CouponType type() {
        return CouponType.RATE;
    }

    @Override
    public void validateValue(CouponValue value) {
        DiscountRate.of(value);
    }

    @Override
    public CouponMoney calculateDiscount(CouponMoney orderAmount, CouponValue value) {
        return DiscountRate.of(value).discount(orderAmount);
    }
}
