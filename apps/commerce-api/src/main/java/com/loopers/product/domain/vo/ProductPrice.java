package com.loopers.product.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record ProductPrice(long value) {

    public ProductPrice {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0 이상이어야 합니다.");
        }
    }

    public static ProductPrice of(long value) {
        return new ProductPrice(value);
    }
}
