package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponMoneyTest {

    @DisplayName("0 이상의 금액이 주어지면, 쿠폰 금액을 생성한다.")
    @Test
    void createsCouponMoney_whenValueIsNotNegative() {
        // arrange
        long amount = 2_000L;

        // act
        CouponMoney couponMoney = CouponMoney.of(amount);

        // assert
        assertThat(couponMoney.value()).isEqualTo(amount);
    }

    @DisplayName("금액이 음수이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsNegative() {
        // arrange
        long amount = -1L;

        // act & assert
        assertThatThrownBy(() -> CouponMoney.of(amount))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("두 쿠폰 금액 중 작은 금액을 반환한다.")
    @Test
    void returnsMinMoney_whenOtherMoneyIsProvided() {
        // arrange
        CouponMoney fixedDiscountAmount = CouponMoney.of(2_000L);
        CouponMoney orderAmount = CouponMoney.of(12_000L);

        // act
        CouponMoney minAmount = fixedDiscountAmount.min(orderAmount);

        // assert
        assertThat(minAmount).isEqualTo(fixedDiscountAmount);
    }

    @DisplayName("쿠폰 금액에서 다른 금액을 차감하면, 차감된 금액을 반환한다.")
    @Test
    void returnsSubtractedMoney_whenOtherMoneyIsProvided() {
        // arrange
        CouponMoney orderAmount = CouponMoney.of(12_000L);
        CouponMoney discountAmount = CouponMoney.of(2_000L);

        // act
        CouponMoney paymentAmount = orderAmount.minus(discountAmount);

        // assert
        assertThat(paymentAmount.value()).isEqualTo(10_000L);
    }
}
