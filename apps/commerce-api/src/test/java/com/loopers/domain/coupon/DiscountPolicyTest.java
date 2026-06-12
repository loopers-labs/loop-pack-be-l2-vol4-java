package com.loopers.domain.coupon;

import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscountPolicyTest {

    @Test
    void calculatesPercentageDiscount_whenTypeIsRate() {
        // arrange — 10% 정률 쿠폰, 최소주문금액은 일단 0
        DiscountPolicy policy = DiscountPolicy.of(DiscountType.RATE, 10, Money.of(0));

        // act
        Money discount = policy.calculateDiscount(Money.of(23_000));

        // assert
        assertThat(discount).isEqualTo(Money.of(2_300));
    }

    @Test
    void deductsFixedAmount_whenTypeIsFixed() {
        // arrange — 3,000원 정액 쿠폰
        DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, 3_000, Money.of(0));

        // act
        Money discount = policy.calculateDiscount(Money.of(7_000));

        // assert
        assertThat(discount).isEqualTo(Money.of(3_000));
    }

    @Test
    void capsDiscountAtOrderAmount_whenFixedExceedsOrder() {
        // arrange — 10,000원 정액 쿠폰인데 주문은 7,000원 (더 깎으면 음수)
        DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, 10_000, Money.of(0));

        // act
        Money discount = policy.calculateDiscount(Money.of(7_000));

        // assert — 주문금액까지만 할인 (음수 결제 방지)
        assertThat(discount).isEqualTo(Money.of(7_000));
    }

    @Test
    void throwsBadRequest_whenOrderBelowMinOrderAmount() {
        // arrange — 최소주문 10,000원 쿠폰
        DiscountPolicy policy = DiscountPolicy.of(DiscountType.FIXED, 3_000, Money.of(10_000));

        // act
        CoreException result = assertThrows(CoreException.class, () ->
                policy.calculateDiscount(Money.of(9_999)));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
