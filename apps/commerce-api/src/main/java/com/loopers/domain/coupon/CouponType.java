package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum CouponType {
    FIXED {
        @Override
        public long discount(long orderAmount, long value) {
            return Math.min(value, orderAmount);
        }

        @Override
        public void validateValue(long value) {
            if (value < 1) {
                throw new CoreException(ErrorType.INVALID_COUPON_VALUE, "정액 할인 금액은 1 이상이어야 합니다.");
            }
        }
    },
    RATE {
        @Override
        public long discount(long orderAmount, long value) {
            return orderAmount * value / 100;
        }

        @Override
        public void validateValue(long value) {
            if (value < 1 || value > 100) {
                throw new CoreException(ErrorType.INVALID_COUPON_VALUE, "정률 할인율은 1 이상 100 이하여야 합니다.");
            }
        }
    };

    public abstract long discount(long orderAmount, long value);

    public abstract void validateValue(long value);
}
