package com.loopers.domain.coupon.policy;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.domain.coupon.vo.DiscountValue;

public interface CouponDiscountPolicy {

    CouponType type();

    void confirmDiscountValue(DiscountValue discountValue);

    CouponMoney discount(CouponMoney orderAmount, DiscountValue discountValue);
}
