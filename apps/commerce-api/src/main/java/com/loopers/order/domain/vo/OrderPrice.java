package com.loopers.order.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record OrderPrice(long value) {

    public OrderPrice {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.");
        }
    }

    public static OrderPrice of(long value) {
        return new OrderPrice(value);
    }

    public OrderPrice multiply(OrderQuantity quantity) {
        return OrderPrice.of(value * quantity.value());
    }

    public OrderPrice add(OrderPrice other) {
        return OrderPrice.of(value + other.value);
    }
}
