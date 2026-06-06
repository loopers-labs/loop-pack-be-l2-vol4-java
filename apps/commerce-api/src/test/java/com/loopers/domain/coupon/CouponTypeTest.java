package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTypeTest {

    @DisplayName("정액(FIXED) 쿠폰의 할인액을 계산할 때, ")
    @Nested
    class Fixed {

        @DisplayName("정액이 주문금액보다 작으면, 정액 그대로 할인된다.")
        @Test
        void discountsValue_whenValueIsLessThanOrderAmount() {
            // given
            long orderAmount = 10_000L;
            long value = 3_000L;

            // when
            long discount = CouponType.FIXED.discount(orderAmount, value);

            // then
            assertThat(discount).isEqualTo(3_000L);
        }

        @DisplayName("정액이 주문금액보다 크면, 주문금액까지만 할인된다.")
        @Test
        void discountsUpToOrderAmount_whenValueIsGreaterThanOrderAmount() {
            // given
            long orderAmount = 2_000L;
            long value = 3_000L;

            // when
            long discount = CouponType.FIXED.discount(orderAmount, value);

            // then
            assertThat(discount).isEqualTo(2_000L);
        }

        @DisplayName("정액이 주문금액과 같으면, 주문금액 전액이 할인된다.")
        @Test
        void discountsAll_whenValueEqualsOrderAmount() {
            // given
            long orderAmount = 5_000L;
            long value = 5_000L;

            // when
            long discount = CouponType.FIXED.discount(orderAmount, value);

            // then
            assertThat(discount).isEqualTo(5_000L);
        }

        @DisplayName("정액이 0이면, 할인액은 0이다.")
        @Test
        void discountsZero_whenValueIsZero() {
            // given
            long orderAmount = 10_000L;
            long value = 0L;

            // when
            long discount = CouponType.FIXED.discount(orderAmount, value);

            // then
            assertThat(discount).isEqualTo(0L);
        }
    }

    @DisplayName("정률(RATE) 쿠폰의 할인액을 계산할 때, ")
    @Nested
    class Rate {

        @DisplayName("나누어떨어지는 비율이면, 비율만큼 할인된다.")
        @Test
        void discountsByRate_whenDivisible() {
            // given
            long orderAmount = 10_000L;
            long value = 10L;

            // when
            long discount = CouponType.RATE.discount(orderAmount, value);

            // then
            assertThat(discount).isEqualTo(1_000L);
        }

        @DisplayName("소수점이 발생하면, 내림 처리된다.")
        @Test
        void discountsWithFloor_whenResultHasFraction() {
            // given - 1,055 * 10% = 105.5
            long orderAmount = 1_055L;
            long value = 10L;

            // when
            long discount = CouponType.RATE.discount(orderAmount, value);

            // then
            assertThat(discount).isEqualTo(105L);
        }

        @DisplayName("비율이 100이면, 주문금액 전액이 할인된다.")
        @Test
        void discountsAll_whenRateIsHundred() {
            // given
            long orderAmount = 7_777L;
            long value = 100L;

            // when
            long discount = CouponType.RATE.discount(orderAmount, value);

            // then
            assertThat(discount).isEqualTo(7_777L);
        }

        @DisplayName("비율이 0이면, 할인액은 0이다.")
        @Test
        void discountsZero_whenRateIsZero() {
            // given
            long orderAmount = 10_000L;
            long value = 0L;

            // when
            long discount = CouponType.RATE.discount(orderAmount, value);

            // then
            assertThat(discount).isEqualTo(0L);
        }
    }

    @DisplayName("정액(FIXED) 쿠폰의 값을 검증할 때, ")
    @Nested
    class ValidateFixedValue {

        @DisplayName("값이 1이면, 정상적으로 통과한다.")
        @Test
        void passes_whenValueIsOne() {
            // given
            long value = 1L;

            // when & then
            assertThatCode(() -> CouponType.FIXED.validateValue(value))
                .doesNotThrowAnyException();
        }

        @DisplayName("값이 0이면, INVALID_COUPON_VALUE 예외가 발생한다.")
        @Test
        void throwsInvalidCouponValueException_whenValueIsZero() {
            // given
            long value = 0L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> CouponType.FIXED.validateValue(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
                () -> assertThat(result.getCustomMessage()).isEqualTo("정액 할인 금액은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("값이 음수이면, INVALID_COUPON_VALUE 예외가 발생한다.")
        @Test
        void throwsInvalidCouponValueException_whenValueIsNegative() {
            // given
            long value = -1L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> CouponType.FIXED.validateValue(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
                () -> assertThat(result.getCustomMessage()).isEqualTo("정액 할인 금액은 1 이상이어야 합니다.")
            );
        }
    }

    @DisplayName("정률(RATE) 쿠폰의 값을 검증할 때, ")
    @Nested
    class ValidateRateValue {

        @DisplayName("값이 1이면, 정상적으로 통과한다.")
        @Test
        void passes_whenValueIsOne() {
            // given
            long value = 1L;

            // when & then
            assertThatCode(() -> CouponType.RATE.validateValue(value))
                .doesNotThrowAnyException();
        }

        @DisplayName("값이 100이면, 정상적으로 통과한다.")
        @Test
        void passes_whenValueIsHundred() {
            // given
            long value = 100L;

            // when & then
            assertThatCode(() -> CouponType.RATE.validateValue(value))
                .doesNotThrowAnyException();
        }

        @DisplayName("값이 0이면, INVALID_COUPON_VALUE 예외가 발생한다.")
        @Test
        void throwsInvalidCouponValueException_whenValueIsZero() {
            // given
            long value = 0L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> CouponType.RATE.validateValue(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
                () -> assertThat(result.getCustomMessage()).isEqualTo("정률 할인율은 1 이상 100 이하여야 합니다.")
            );
        }

        @DisplayName("값이 100을 초과하면, INVALID_COUPON_VALUE 예외가 발생한다.")
        @Test
        void throwsInvalidCouponValueException_whenValueExceedsHundred() {
            // given
            long value = 101L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> CouponType.RATE.validateValue(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
                () -> assertThat(result.getCustomMessage()).isEqualTo("정률 할인율은 1 이상 100 이하여야 합니다.")
            );
        }

        @DisplayName("값이 음수이면, INVALID_COUPON_VALUE 예외가 발생한다.")
        @Test
        void throwsInvalidCouponValueException_whenValueIsNegative() {
            // given
            long value = -1L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> CouponType.RATE.validateValue(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
                () -> assertThat(result.getCustomMessage()).isEqualTo("정률 할인율은 1 이상 100 이하여야 합니다.")
            );
        }
    }
}
