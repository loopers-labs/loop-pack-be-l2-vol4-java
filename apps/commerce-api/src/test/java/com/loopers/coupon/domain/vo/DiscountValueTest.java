package com.loopers.coupon.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountValueTest {

    @DisplayName("0보다 큰 값이 주어지면, 쿠폰 정책 값을 생성한다.")
    @Test
    void createsDiscountValue_whenValueIsPositive() {
        // arrange
        long discountValue = 10L;

        // act
        DiscountValue discount = DiscountValue.of(discountValue);

        // assert
        assertThat(discount.value()).isEqualTo(discountValue);
    }

    @DisplayName("쿠폰 정책 값이 0 이하이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsNotPositive() {
        // arrange
        long discountValue = 0L;

        // act & assert
        assertThatThrownBy(() -> DiscountValue.of(discountValue))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
