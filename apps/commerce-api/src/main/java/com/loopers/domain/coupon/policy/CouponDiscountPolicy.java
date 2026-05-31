package com.loopers.domain.coupon.policy;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.domain.coupon.vo.CouponValue;

public interface CouponDiscountPolicy {

    CouponType type();

    void validateValue(CouponValue value);

    CouponMoney calculateDiscount(CouponMoney orderAmount, CouponValue value);
}
