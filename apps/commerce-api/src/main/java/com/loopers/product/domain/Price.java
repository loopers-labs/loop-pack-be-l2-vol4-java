package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record Price(Long value) {

    public Price {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 비어있을 수 없습니다.");
        }
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }
}
