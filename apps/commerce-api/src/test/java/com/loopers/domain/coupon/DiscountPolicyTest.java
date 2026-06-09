package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscountPolicyTest {

    @DisplayName("정액 할인 정책을 생성할 때,")
    @Nested
    class Fixed {

        @DisplayName("양수 금액으로 정액 할인 정책이 생성되면 입력값이 설정된다.")
        @Test
        void fixedDiscountPolicyIsCreated_whenValueIsPositive() {
            // when
            DiscountPolicy policy = new DiscountPolicy(CouponType.FIXED, BigDecimal.valueOf(5000));

            // then
            assertAll(
                    () -> assertThat(policy.type()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(policy.value()).isEqualByComparingTo(BigDecimal.valueOf(5000))
            );
        }

        @DisplayName("할인 금액이 0 이하이면 정액 할인 정책을 생성할 수 없다.")
        @ValueSource(strings = {"0", "-1", "-1000"})
        @ParameterizedTest
        void fixedDiscountPolicyCannotBeCreated_whenValueIsNotPositive(String value) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new DiscountPolicy(CouponType.FIXED, new BigDecimal(value)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 금액이 null이면 정액 할인 정책을 생성할 수 없다.")
        @Test
        void fixedDiscountPolicyCannotBeCreated_whenValueIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new DiscountPolicy(CouponType.FIXED, null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비율 할인 정책을 생성할 때,")
    @Nested
    class Rate {

        @DisplayName("0 초과 100 이하 할인율로 비율 할인 정책이 생성되면 입력값이 설정된다.")
        @Test
        void rateDiscountPolicyIsCreated_whenValueIsInRange() {
            // when
            DiscountPolicy policy = new DiscountPolicy(CouponType.RATE, BigDecimal.valueOf(10));

            // then
            assertAll(
                    () -> assertThat(policy.type()).isEqualTo(CouponType.RATE),
                    () -> assertThat(policy.value()).isEqualByComparingTo(BigDecimal.valueOf(10))
            );
        }

        @DisplayName("할인율이 0 이하이면 비율 할인 정책을 생성할 수 없다.")
        @ValueSource(strings = {"0", "-1", "-100"})
        @ParameterizedTest
        void rateDiscountPolicyCannotBeCreated_whenValueIsNotPositive(String value) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new DiscountPolicy(CouponType.RATE, new BigDecimal(value)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인율이 100을 초과하면 비율 할인 정책을 생성할 수 없다.")
        @ValueSource(strings = {"101", "200"})
        @ParameterizedTest
        void rateDiscountPolicyCannotBeCreated_whenValueExceeds100(String value) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new DiscountPolicy(CouponType.RATE, new BigDecimal(value)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인율이 null이면 비율 할인 정책을 생성할 수 없다.")
        @Test
        void rateDiscountPolicyCannotBeCreated_whenValueIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new DiscountPolicy(CouponType.RATE, null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인 타입이 null이면 할인 정책을 생성할 수 없다.")
    @Test
    void discountPolicyCannotBeCreated_whenTypeIsNull() {
        // when
        CoreException result = assertThrows(CoreException.class,
                () -> new DiscountPolicy(null, BigDecimal.valueOf(10)));

        // then
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
