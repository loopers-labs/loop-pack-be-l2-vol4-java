package com.loopers.domain.coupon;

import com.loopers.domain.vo.Money;

public enum DiscountType {

    RATE {
        @Override
        public Money discount(long value, Money orderAmount) {
            // 정률: 주문금액 * value% (정수 나눗셈 = 버림)
            return Money.of(orderAmount.amount() * value / 100);
        }
    },

    FIXED {
        @Override
        public Money discount(long value, Money orderAmount) {
            // 정액: value 원 할인. 단 주문금액보다 더 깎을 순 없음 (음수 결제 방지)
            Money fixed = Money.of(value);
            return orderAmount.isGreaterThanOrEqual(fixed) ? fixed : orderAmount;
        }
    };

    public abstract Money discount(long value, Money orderAmount);
}
