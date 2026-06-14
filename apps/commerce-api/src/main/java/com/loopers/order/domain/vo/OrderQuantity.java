package com.loopers.order.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record OrderQuantity(int value) {

    public OrderQuantity {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }

    public static OrderQuantity of(int value) {
        return new OrderQuantity(value);
    }
}
