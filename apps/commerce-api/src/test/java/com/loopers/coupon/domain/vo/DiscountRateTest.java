package com.loopers.coupon.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountRateTest {

    @DisplayName("1에서 100 사이의 값이 주어지면, 할인율을 생성한다.")
    @Test
    void createsDiscountRate_whenValueIsBetweenOneAndHundred() {
        // arrange
        DiscountValue discountValue = DiscountValue.of(10L);

        // act
        DiscountRate discountRate = DiscountRate.of(discountValue);

        // assert
        assertThat(discountRate.value()).isEqualTo(10L);
    }

    @DisplayName("할인율이 1에서 100 사이가 아니면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsOutOfRange() {
        // arrange
        DiscountValue discountValue = DiscountValue.of(101L);

        // act & assert
        assertThatThrownBy(() -> DiscountRate.of(discountValue))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("할인율을 쿠폰 금액에 적용하면, 원 단위 미만을 절사한 할인 금액을 반환한다.")
    @Test
    void returnsDiscountMoney_whenAppliedToCouponMoney() {
        // arrange
        DiscountRate discountRate = DiscountRate.of(DiscountValue.of(10L));
        CouponMoney orderAmount = CouponMoney.of(12_345L);

        // act
        CouponMoney discount = discountRate.discount(orderAmount);

        // assert
        assertThat(discount.value()).isEqualTo(1_234L);
    }
}
