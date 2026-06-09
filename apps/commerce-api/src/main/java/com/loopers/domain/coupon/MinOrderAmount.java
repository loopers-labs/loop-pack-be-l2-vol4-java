package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record MinOrderAmount(
    @Column(name = "min_order_amount")
    Integer value
) {

    private static final int MIN_VALUE = 1;

    public static MinOrderAmount from(Integer value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 필수입니다.");
        }

        if (value < MIN_VALUE) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("최소 주문 금액은 %d 이상만 허용됩니다.", MIN_VALUE));
        }

        return new MinOrderAmount(value);
    }
}
