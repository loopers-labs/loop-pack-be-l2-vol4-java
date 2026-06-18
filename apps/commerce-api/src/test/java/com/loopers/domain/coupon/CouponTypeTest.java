package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CouponTypeTest {

    @Nested
    @DisplayName("FIXED 정액 할인")
    class Fixed {

        @DisplayName("할인 값만큼 깎는다")
        @Test
        void given_amountAboveValue_when_discount_then_fixedValue() {
            assertThat(CouponType.FIXED.discount(3000L, 10000L)).isEqualTo(3000L);
        }

        @DisplayName("할인 값이 주문 금액보다 크면 주문 금액만큼만 깎는다 (음수 방지)")
        @Test
        void given_valueAboveAmount_when_discount_then_cappedAtAmount() {
            assertThat(CouponType.FIXED.discount(15000L, 10000L)).isEqualTo(10000L);
        }
    }

    @Nested
    @DisplayName("RATE 정률 할인")
    class Rate {

        @DisplayName("주문 금액의 value(%)만큼 깎는다")
        @Test
        void given_amount_when_discount_then_percentage() {
            assertThat(CouponType.RATE.discount(10L, 10000L)).isEqualTo(1000L);
        }

        @DisplayName("원 단위 미만은 버린다 (floor)")
        @Test
        void given_nonDivisible_when_discount_then_floored() {
            // 9999 * 10 / 100 = 999.9 → 999
            assertThat(CouponType.RATE.discount(10L, 9999L)).isEqualTo(999L);
        }

        @DisplayName("100%면 주문 금액 전부를 깎는다")
        @Test
        void given_hundredPercent_when_discount_then_fullAmount() {
            assertThat(CouponType.RATE.discount(100L, 10000L)).isEqualTo(10000L);
        }
    }
}
