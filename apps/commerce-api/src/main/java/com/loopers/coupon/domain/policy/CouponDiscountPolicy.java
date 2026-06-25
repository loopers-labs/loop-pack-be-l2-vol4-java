package com.loopers.coupon.domain.policy;

import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.vo.CouponMoney;
import com.loopers.coupon.domain.vo.DiscountValue;

public interface CouponDiscountPolicy {

    CouponType type();

    void confirmDiscountValue(DiscountValue discountValue);

    CouponMoney discount(CouponMoney orderAmount, DiscountValue discountValue);
}
