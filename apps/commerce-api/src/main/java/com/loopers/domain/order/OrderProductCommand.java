package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record OrderProductCommand(
    Long productId,
    Integer quantity
) {
    public OrderProductCommand {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        if (quantity == null || quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
