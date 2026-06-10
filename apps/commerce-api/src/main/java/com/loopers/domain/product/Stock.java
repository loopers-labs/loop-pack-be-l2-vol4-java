package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Stock(
    @Column(name = "stock", nullable = false)
    Integer value
) {

    private static final int MIN_QUANTITY = 0;

    public static Stock from(Integer value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 재고는 필수입니다.");
        }

        if (value < MIN_QUANTITY) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("상품 재고는 %d 이상만 허용됩니다.", MIN_QUANTITY));
        }

        return new Stock(value);
    }

    public Stock decrease(int quantity) {
        if (value < quantity) {
            throw new CoreException(ErrorType.CONFLICT, "상품 재고가 부족합니다.");
        }

        return new Stock(value - quantity);
    }
}
