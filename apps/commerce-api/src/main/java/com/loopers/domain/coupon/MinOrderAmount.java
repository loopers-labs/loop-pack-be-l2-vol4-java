package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record MinOrderAmount(
    @Column(name = "min_order_amount", nullable = false)
    Integer value
) {

    private static final int MIN_VALUE = 0;

    public static MinOrderAmount from(Integer value) {
        if (value == null) {
            return new MinOrderAmount(MIN_VALUE);
        }

        if (value < MIN_VALUE) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("최소 주문 금액은 %d 이상만 허용됩니다.", MIN_VALUE));
        }

        return new MinOrderAmount(value);
    }
}
