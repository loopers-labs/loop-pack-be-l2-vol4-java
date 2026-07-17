package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record OrderLine(Long productId, int quantity) {
    public OrderLine {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 식별자는 필수입니다.");
        }
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
