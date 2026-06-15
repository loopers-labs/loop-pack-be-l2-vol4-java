package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.util.CollectionUtils;

import java.util.List;

public final class OrderItems {

    private final List<OrderItem> items;

    private OrderItems(List<OrderItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        this.items = items;
    }

    public static OrderItems from(List<OrderItem> items) {
        return new OrderItems(items);
    }


    public Money totalAmount() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.of(0), Money::add);
    }

    public List<OrderItem> asList() {
        return List.copyOf(items);
    }
}
