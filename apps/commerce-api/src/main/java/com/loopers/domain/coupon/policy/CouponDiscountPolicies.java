package com.loopers.domain.coupon.policy;

import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CouponDiscountPolicies {

    private final Map<CouponType, CouponDiscountPolicy> policies;

    public CouponDiscountPolicies(List<CouponDiscountPolicy> policies) {
        this.policies = toPolicyMap(policies);
    }

    public CouponDiscountPolicy get(CouponType type) {
        CouponDiscountPolicy policy = policies.get(type);
        if (policy == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "지원하지 않는 쿠폰 할인 정책입니다.");
        }
        return policy;
    }

    private static Map<CouponType, CouponDiscountPolicy> toPolicyMap(List<CouponDiscountPolicy> policies) {
        Map<CouponType, CouponDiscountPolicy> policyMap = new EnumMap<>(CouponType.class);
        for (CouponDiscountPolicy policy : policies) {
            CouponDiscountPolicy previous = policyMap.put(policy.type(), policy);
            if (previous != null) {
                throw new CoreException(ErrorType.INTERNAL_ERROR, "중복된 쿠폰 할인 정책입니다.");
            }
        }
        return policyMap;
    }
}
