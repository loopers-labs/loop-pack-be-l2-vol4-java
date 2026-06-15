package com.loopers.domain.coupon;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscountPolicyTest {

    @DisplayName("DiscountPolicy 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정액(FIXED) 은 1 이상의 값이면 생성된다.")
        @ParameterizedTest
        @ValueSource(longs = {1L, 1_000L, Long.MAX_VALUE})
        void createsFixed_whenValueIsPositive(long value) {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, value);
            assertThat(policy.getType()).isEqualTo(DiscountType.FIXED);
            assertThat(policy.getValue()).isEqualTo(value);
        }

        @DisplayName("정률(RATE) 은 1~100 사이면 생성된다.")
        @ParameterizedTest
        @ValueSource(longs = {1L, 50L, 100L})
        void createsRate_whenValueInRange(long value) {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.RATE, value);
            assertThat(policy.getType()).isEqualTo(DiscountType.RATE);
            assertThat(policy.getValue()).isEqualTo(value);
        }

        @DisplayName("종류가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTypeIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> DiscountPolicy.of(null, 1_000L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값이 1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        void throwsBadRequest_whenValueIsNotPositive(long value) {
            CoreException result = assertThrows(CoreException.class, () -> DiscountPolicy.of(DiscountType.FIXED, value));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 값이 100 을 넘으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {101L, 1_000L})
        void throwsBadRequest_whenRateExceeds100(long value) {
            CoreException result = assertThrows(CoreException.class, () -> DiscountPolicy.of(DiscountType.RATE, value));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("정액(FIXED) 할인액을 계산할 때, ")
    @Nested
    class CalculateFixed {

        @DisplayName("적용 전 금액보다 작으면 고정 금액만큼 할인한다.")
        @Test
        void discountsByValue_whenSmallerThanAmount() {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, 3_000L);
            Money discount = policy.calculate(Money.of(10_000L));
            assertThat(discount.getAmount()).isEqualTo(3_000L);
        }

        @DisplayName("적용 전 금액보다 크면 적용 전 금액까지만 할인한다(음수 방지).")
        @Test
        void clampsToAmount_whenLargerThanAmount() {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, 10_000L);
            Money discount = policy.calculate(Money.of(3_000L));
            assertThat(discount.getAmount()).isEqualTo(3_000L);
        }
    }

    @DisplayName("정률(RATE) 할인액을 계산할 때, ")
    @Nested
    class CalculateRate {

        @DisplayName("비율만큼 할인하고 원 단위 미만은 절사한다.")
        @Test
        void discountsByRate_withFloor() {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.RATE, 10L);
            // 9_999 * 10 / 100 = 999.9 -> 999
            Money discount = policy.calculate(Money.of(9_999L));
            assertThat(discount.getAmount()).isEqualTo(999L);
        }

        @DisplayName("100% 면 적용 전 금액 전체를 할인한다.")
        @Test
        void discountsAll_whenRateIs100() {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.RATE, 100L);
            Money discount = policy.calculate(Money.of(5_000L));
            assertThat(discount.getAmount()).isEqualTo(5_000L);
        }

        @DisplayName("계산 중 오버플로우가 나면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOverflow() {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.RATE, 100L);
            CoreException result = assertThrows(CoreException.class,
                    () -> policy.calculate(Money.of(Long.MAX_VALUE)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("적용 전 금액이 null 이면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequest_whenOrderAmountIsNull() {
        DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, 1_000L);
        CoreException result = assertThrows(CoreException.class, () -> policy.calculate(null));
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("최소 주문 금액(minOrderAmount) 이 있을 때, ")
    @Nested
    class MinOrderAmount {

        @DisplayName("적용 전 금액이 최소 주문 금액 이상이면 정상적으로 할인한다.")
        @Test
        void discounts_whenOrderMeetsMinimum() {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, 1_000L, 10_000L);
            Money discount = policy.calculate(Money.of(10_000L));
            assertThat(discount.getAmount()).isEqualTo(1_000L);
        }

        @DisplayName("적용 전 금액이 최소 주문 금액 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderBelowMinimum() {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, 1_000L, 10_000L);
            CoreException result = assertThrows(CoreException.class, () -> policy.calculate(Money.of(9_999L)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액이 음수면 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMinOrderAmountIsNegative() {
            CoreException result = assertThrows(CoreException.class,
                    () -> DiscountPolicy.of(DiscountType.FIXED, 1_000L, -1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액 0(제한 없음) 이면 어떤 금액에도 할인한다.")
        @Test
        void discountsAny_whenNoMinimum() {
            DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, 1_000L, 0L);
            Money discount = policy.calculate(Money.of(1L));
            assertThat(discount.getAmount()).isEqualTo(1L);
        }
    }
}
