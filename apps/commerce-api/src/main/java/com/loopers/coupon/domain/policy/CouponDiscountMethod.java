package com.loopers.coupon.domain.policy;

import com.loopers.coupon.domain.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CouponDiscountMethod {

    private final Map<CouponType, CouponDiscountPolicy> policies;

    public CouponDiscountMethod(List<CouponDiscountPolicy> policies) {
        this.policies = toPolicyMap(policies);
    }

    public CouponDiscountPolicy match(CouponType type) {
        CouponDiscountPolicy policy = policies.get(type);
        if (policy == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "지원하지 않는 쿠폰 할인 방식입니다.");
        }
        return policy;
    }

    private static Map<CouponType, CouponDiscountPolicy> toPolicyMap(List<CouponDiscountPolicy> policies) {
        Map<CouponType, CouponDiscountPolicy> policyMap = new EnumMap<>(CouponType.class);
        for (CouponDiscountPolicy policy : policies) {
            CouponDiscountPolicy previous = policyMap.put(policy.type(), policy);
            if (previous != null) {
                throw new CoreException(ErrorType.INTERNAL_ERROR, "중복된 쿠폰 할인 방식입니다.");
            }
        }
        return policyMap;
    }
}
