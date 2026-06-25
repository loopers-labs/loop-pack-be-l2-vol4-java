package com.loopers.coupon.domain.policy;

import com.loopers.coupon.domain.CouponType;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponDiscountMethodTest {

    @DisplayName("쿠폰 타입이 주어지면, 해당 타입의 할인 방식을 반환한다.")
    @Test
    void returnsPolicy_whenCouponTypeIsProvided() {
        // arrange
        CouponDiscountMethod discountMethod = new CouponDiscountMethod(List.of(
            new FixedCouponDiscountPolicy(),
            new RateCouponDiscountPolicy()
        ));

        // act
        CouponDiscountPolicy policy = discountMethod.match(CouponType.FIXED);

        // assert
        assertThat(policy).isInstanceOf(FixedCouponDiscountPolicy.class);
    }

    @DisplayName("쿠폰 타입을 지원하는 할인 방식이 없으면, INTERNAL_ERROR 예외를 던진다.")
    @Test
    void throwsInternalError_whenPolicyDoesNotExist() {
        // arrange
        CouponDiscountMethod discountMethod = new CouponDiscountMethod(List.of(new FixedCouponDiscountPolicy()));

        // act & assert
        assertThatThrownBy(() -> discountMethod.match(CouponType.RATE))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.INTERNAL_ERROR);
    }

    @DisplayName("같은 타입의 할인 방식이 중복되면, INTERNAL_ERROR 예외를 던진다.")
    @Test
    void throwsInternalError_whenPolicyIsDuplicated() {
        // arrange
        List<CouponDiscountPolicy> policies = List.of(
            new FixedCouponDiscountPolicy(),
            new FixedCouponDiscountPolicy()
        );

        // act & assert
        assertThatThrownBy(() -> new CouponDiscountMethod(policies))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.INTERNAL_ERROR);
    }
}
