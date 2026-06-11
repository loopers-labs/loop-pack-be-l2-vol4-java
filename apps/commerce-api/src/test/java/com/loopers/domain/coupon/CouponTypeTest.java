package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTypeTest {

    @DisplayName("정액(FIXED) 쿠폰 할인 계산 시")
    @Nested
    class Fixed {

        @DisplayName("할인 값이 주문 금액보다 작으면 할인 값만큼 할인된다")
        @Test
        void discountsByValue_whenValueIsLessThanOrderAmount() {
            // given
            Money orderAmount = Money.of(10_000L);

            // when
            Money discount = CouponType.FIXED.discount(orderAmount, 3_000L);

            // then
            assertThat(discount).isEqualTo(Money.of(3_000L));
        }

        @DisplayName("할인 값이 주문 금액을 초과하면 주문 금액까지만 할인된다")
        @Test
        void discountsUpToOrderAmount_whenValueExceedsOrderAmount() {
            // given
            Money orderAmount = Money.of(10_000L);

            // when
            Money discount = CouponType.FIXED.discount(orderAmount, 15_000L);

            // then
            assertThat(discount).isEqualTo(Money.of(10_000L));
        }
    }

    @DisplayName("정률(RATE) 쿠폰 할인 계산 시")
    @Nested
    class Rate {

        @DisplayName("주문 금액의 value% 만큼 할인된다")
        @Test
        void discountsByPercentage() {
            // given
            Money orderAmount = Money.of(10_000L);

            // when
            Money discount = CouponType.RATE.discount(orderAmount, 10L);

            // then
            assertThat(discount).isEqualTo(Money.of(1_000L));
        }

        @DisplayName("할인액에 원 미만이 발생하면 원 단위로 내림한다")
        @Test
        void floorsToWon_whenFractionOccurs() {
            // given - 10005 * 10 / 100 = 1000.5 → 1000
            Money orderAmount = Money.of(10_005L);

            // when
            Money discount = CouponType.RATE.discount(orderAmount, 10L);

            // then
            assertThat(discount).isEqualTo(Money.of(1_000L));
        }

        @DisplayName("주문 금액이 매우 커서 할인 계산이 long 범위를 초과하면 BAD_REQUEST 예외가 발생하고 원인이 보존된다")
        @Test
        void throwsBadRequest_whenCalculationOverflows() {
            // given - Long.MAX_VALUE * 10 은 long 곱셈 범위를 초과한다
            Money orderAmount = Money.of(Long.MAX_VALUE);

            // when
            CoreException ex = assertThrows(CoreException.class, () -> CouponType.RATE.discount(orderAmount, 10L));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(ex.getCause()).isInstanceOf(ArithmeticException.class);
        }
    }

    @DisplayName("할인 값 유효성 검증 시")
    @Nested
    class ValidateValue {

        @DisplayName("정액 쿠폰은 1원 이상이면 통과하고, 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void fixed_passesWhenPositive_throwsWhenNotPositive() {
            // when / then
            assertDoesNotThrow(() -> CouponType.FIXED.validateValue(1L));

            CoreException ex = assertThrows(CoreException.class, () -> CouponType.FIXED.validateValue(0L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰은 1~100 사이이면 통과하고, 범위를 벗어나면 BAD_REQUEST 예외가 발생한다")
        @Test
        void rate_passesWithinRange_throwsOutOfRange() {
            // when / then
            assertDoesNotThrow(() -> CouponType.RATE.validateValue(1L));
            assertDoesNotThrow(() -> CouponType.RATE.validateValue(100L));

            CoreException tooLow = assertThrows(CoreException.class, () -> CouponType.RATE.validateValue(0L));
            CoreException tooHigh = assertThrows(CoreException.class, () -> CouponType.RATE.validateValue(101L));
            assertThat(tooLow.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(tooHigh.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
