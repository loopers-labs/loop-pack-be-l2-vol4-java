package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponValueTest {

    @DisplayName("0보다 큰 값이 주어지면, 쿠폰 정책 값을 생성한다.")
    @Test
    void createsCouponValue_whenValueIsPositive() {
        // arrange
        long value = 10L;

        // act
        CouponValue couponValue = CouponValue.of(value);

        // assert
        assertThat(couponValue.value()).isEqualTo(value);
    }

    @DisplayName("쿠폰 정책 값이 0 이하이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsNotPositive() {
        // arrange
        long value = 0L;

        // act & assert
        assertThatThrownBy(() -> CouponValue.of(value))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
