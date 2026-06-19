package com.loopers.product.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record ProductDescription(String value) {

    public ProductDescription {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
    }

    public static ProductDescription of(String value) {
        return new ProductDescription(value);
    }
}
