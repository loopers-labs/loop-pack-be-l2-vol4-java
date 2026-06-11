package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record ProductPrice(Long value) {

    public ProductPrice {
        if (value == null || value < 0L) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }

    public static ProductPrice of(Long value) {
        return new ProductPrice(value);
    }
}