package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateTest {

    @DisplayName("정액 쿠폰은 주문 금액을 넘지 않는 범위에서 할인한다.")
    @Test
    void capsFixedDiscountAtOriginalAmount() {
        CouponTemplate template = template(CouponType.FIXED, 5_000L, null);

        assertThat(template.calculateDiscount(3_000L)).isEqualTo(3_000L);
    }

    @DisplayName("정률 쿠폰은 원 단위로 반올림해 할인한다.")
    @Test
    void roundsRateDiscountToWon() {
        CouponTemplate template = template(CouponType.RATE, 15L, null);

        assertThat(template.calculateDiscount(333L)).isEqualTo(50L);
    }

    @DisplayName("할인 전 상품 합계가 최소 주문 금액보다 작으면 쿠폰을 적용할 수 없다.")
    @Test
    void rejectsDiscount_whenOriginalAmountIsBelowMinimum() {
        CouponTemplate template = template(CouponType.FIXED, 1_000L, 10_000L);

        assertThrows(CoreException.class, () -> template.calculateDiscount(9_999L));
    }

    @DisplayName("정률 쿠폰 할인율은 100 이하이어야 한다.")
    @Test
    void rejectsRateAboveOneHundred() {
        assertThrows(CoreException.class, () -> template(CouponType.RATE, 101L, null));
    }

    private CouponTemplate template(CouponType type, Long value, Long minOrderAmount) {
        return new CouponTemplate(
            "테스트 쿠폰",
            type,
            value,
            minOrderAmount,
            1,
            ZonedDateTime.now().plusDays(1)
        );
    }
}
