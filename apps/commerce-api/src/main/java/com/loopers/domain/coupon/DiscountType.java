package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum DiscountType {

    FIXED {
        @Override
        void validateRange(int value) {
            if (value < MIN_VALUE) {
                throw new CoreException(ErrorType.BAD_REQUEST, String.format("정액 할인 값은 %d 이상만 허용됩니다.", MIN_VALUE));
            }
        }
    },
    RATE {
        @Override
        void validateRange(int value) {
            if (value < MIN_VALUE || value > MAX_RATE) {
                throw new CoreException(ErrorType.BAD_REQUEST, String.format("정률 할인 값은 %d~%d만 허용됩니다.", MIN_VALUE, MAX_RATE));
            }
        }
    };

    private static final int MIN_VALUE = 1;
    private static final int MAX_RATE = 100;

    public final void validate(Integer value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 필수입니다.");
        }

        validateRange(value);
    }

    abstract void validateRange(int value);
}
