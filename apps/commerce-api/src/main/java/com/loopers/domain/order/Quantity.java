package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Quantity(
    @Column(name = "quantity", nullable = false)
    Integer value
) {

    private static final int MIN_QUANTITY = 1;

    public static Quantity from(Integer value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 필수입니다.");
        }

        if (value < MIN_QUANTITY) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("주문 수량은 %d 이상만 허용됩니다.", MIN_QUANTITY));
        }

        return new Quantity(value);
    }
}
