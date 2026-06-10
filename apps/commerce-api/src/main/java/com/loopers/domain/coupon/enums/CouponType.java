package com.loopers.domain.coupon.enums;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CouponType {
    FIXED("정액 할인") {
        @Override
        public void validate(Long value, Long minOrderAmount) {
            if (value <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "정액 할인 금액은 0보다 커야 합니다.");
            }
        }

        @Override
        public long calculate(long orderAmount, long value) {
            return value;
        }
    },
    RATE("정률 할인") {
        @Override
        public void validate(Long value, Long minOrderAmount) {
            if (value <= 0 || value > 100) {
                throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인율은 1 이상 100 이하여야 합니다.");
            }
        }

        @Override
        public long calculate(long orderAmount, long value) {
            return orderAmount * value / 100;
        }
    };

    private final String description;

    public abstract void validate(Long value, Long minOrderAmount);

    public abstract long calculate(long orderAmount, long value);
}
