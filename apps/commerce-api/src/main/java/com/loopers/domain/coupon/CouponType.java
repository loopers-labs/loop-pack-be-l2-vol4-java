package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum CouponType {
    /** 정액 할인 — value 는 원 단위 */
    FIXED {
        @Override
        public long discountFor(long orderAmount, long value) {
            return Math.min(value, orderAmount);
        }
    },
    /** 정률 할인 — value 는 퍼센트 (0~100) */
    RATE {
        @Override
        public long discountFor(long orderAmount, long value) {
            if (value < 0 || value > 100) {
                throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 0~100 범위여야 합니다.");
            }
            return orderAmount * value / 100;
        }
    };

    /**
     * 주문 금액과 쿠폰 value 로 실제 할인액을 계산한다.
     * 할인액은 주문 금액을 초과하지 않는다.
     */
    public abstract long discountFor(long orderAmount, long value);
}
