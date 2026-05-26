package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Price(
    @Column(name = "price", nullable = false)
    Integer value
) {

    private static final int MIN_VALUE = 0;

    public static Price from(Integer value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 필수입니다.");
        }

        if (value < MIN_VALUE) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("상품 가격은 %d 이상만 허용됩니다.", MIN_VALUE));
        }

        return new Price(value);
    }
}
