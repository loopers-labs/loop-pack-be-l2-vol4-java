package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum CouponType {
    /** 정액 — value 원만큼 할인한다. 주문 금액을 초과하면 주문 금액까지만 할인한다(최종 결제액 음수 방지). */
    FIXED {
        @Override
        public Money discount(Money orderAmount, long value) {
            return Money.of(Math.min(value, orderAmount.value()));
        }

        @Override
        public void validateValue(long value) {
            if (value <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "정액 쿠폰의 할인 금액은 1원 이상이어야 합니다.");
            }
        }
    },
    /** 정률 — 주문 금액의 value% 만큼 할인한다(원 단위 내림). */
    RATE {
        @Override
        public Money discount(Money orderAmount, long value) {
            try {
                return Money.of(Math.multiplyExact(orderAmount.value(), value) / 100);
            } catch (ArithmeticException e) {
                throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액 계산이 표현 범위를 초과했습니다.", e);
            }
        }

        @Override
        public void validateValue(long value) {
            if (value < 1 || value > 100) {
                throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 할인율은 1~100 사이여야 합니다.");
            }
        }
    };

    public abstract Money discount(Money orderAmount, long value);

    /** value의 의미(원/퍼센트)에 따른 유효 범위를 타입이 직접 검증한다. */
    public abstract void validateValue(long value);
}
