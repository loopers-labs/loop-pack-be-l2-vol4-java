package com.loopers.coupon.domain;

import com.loopers.common.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponTypeTest {

    @Test
    @DisplayName("FIXED 는 주문 금액과 무관하게 정액만큼 할인한다")
    void givenFixed_whenDiscount_thenReturnsFixedValue() {
        Money discount = CouponType.FIXED.discount(3_000L, 10_000L);

        assertThat(discount.value()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("RATE 는 주문 금액의 퍼센트만큼 할인하며 소수점은 버린다")
    void givenRate_whenDiscount_thenReturnsFlooredPercentage() {
        // 10% of 12,345 = 1,234.5 -> floor 1,234
        Money discount = CouponType.RATE.discount(10L, 12_345L);

        assertThat(discount.value()).isEqualTo(1_234L);
    }

    @Test
    @DisplayName("FIXED 할인액이 주문 금액보다 크면 주문 금액으로 클램핑한다")
    void givenFixedExceedingOrder_whenDiscount_thenClampsToOrderAmount() {
        Money discount = CouponType.FIXED.discount(10_000L, 4_000L);

        assertThat(discount.value()).isEqualTo(4_000L);
    }

    @Test
    @DisplayName("RATE 100% 는 주문 금액 전액을 할인한다")
    void givenRateHundred_whenDiscount_thenDiscountsWholeOrder() {
        Money discount = CouponType.RATE.discount(100L, 7_000L);

        assertThat(discount.value()).isEqualTo(7_000L);
    }
}
