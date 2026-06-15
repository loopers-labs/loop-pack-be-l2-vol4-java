package com.loopers.domain.coupon;

/**
 * 할인 종류. 종류별 할인액 계산 규칙을 직접 들고 있다(전략 enum).
 * 어느 규칙도 적용 전 금액(amount)을 초과하는 할인액을 돌려주지 않아, "최종 금액 ≥ 0" 불변식을 타입 안에서 지킨다.
 */
public enum DiscountType {

    /** 정액: 고정 금액(원)을 깎되, 적용 전 금액을 넘지 않는다. */
    FIXED {
        @Override
        long discountOf(long amount, long value) {
            return Math.min(value, amount);
        }
    },

    /** 정률: 비율(%)을 적용하되 원 단위 미만은 절사(floor)한다. */
    RATE {
        @Override
        long discountOf(long amount, long value) {
            return Math.floorDiv(Math.multiplyExact(amount, value), 100L);
        }
    };

    /**
     * 적용 전 금액(amount)과 할인 값(value)으로 할인액을 계산한다.
     * @return 0 이상이며 amount 를 초과하지 않는 할인액
     */
    abstract long discountOf(long amount, long value);
}
