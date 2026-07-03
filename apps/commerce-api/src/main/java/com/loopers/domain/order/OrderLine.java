package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 주문 입력 단위 — (상품 ID, 수량).
 * Application 레이어에서 도메인 서비스로 넘기는 가벼운 값 객체.
 */
public record OrderLine(Long productId, Integer quantity) {
    public OrderLine {
        if (productId == null || productId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 양수여야 합니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
