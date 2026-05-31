package com.loopers.domain.coupon.policy;

import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponDiscountPoliciesTest {

    @DisplayName("쿠폰 타입이 주어지면, 해당 타입의 할인 정책을 반환한다.")
    @Test
    void returnsPolicy_whenCouponTypeIsProvided() {
        // arrange
        CouponDiscountPolicies policies = new CouponDiscountPolicies(List.of(
            new FixedCouponDiscountPolicy(),
            new RateCouponDiscountPolicy()
        ));

        // act
        CouponDiscountPolicy policy = policies.get(CouponType.FIXED);

        // assert
        assertThat(policy).isInstanceOf(FixedCouponDiscountPolicy.class);
    }

    @DisplayName("쿠폰 타입을 지원하는 할인 정책이 없으면, INTERNAL_ERROR 예외를 던진다.")
    @Test
    void throwsInternalError_whenPolicyDoesNotExist() {
        // arrange
        CouponDiscountPolicies policies = new CouponDiscountPolicies(List.of(new FixedCouponDiscountPolicy()));

        // act & assert
        assertThatThrownBy(() -> policies.get(CouponType.RATE))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.INTERNAL_ERROR);
    }

    @DisplayName("같은 타입의 할인 정책이 중복되면, INTERNAL_ERROR 예외를 던진다.")
    @Test
    void throwsInternalError_whenPolicyIsDuplicated() {
        // arrange
        List<CouponDiscountPolicy> policies = List.of(
            new FixedCouponDiscountPolicy(),
            new FixedCouponDiscountPolicy()
        );

        // act & assert
        assertThatThrownBy(() -> new CouponDiscountPolicies(policies))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.INTERNAL_ERROR);
    }
}
