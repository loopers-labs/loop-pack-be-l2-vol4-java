package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record OrderItems(List<OrderItem> values) {

    public OrderItems {
        if (values == null || values.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        Set<Long> productIds = values.stream()
            .map(OrderItem::getProductId)
            .collect(Collectors.toSet());
        if (productIds.size() != values.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "같은 상품은 중복 주문할 수 없습니다.");
        }
        values = List.copyOf(values);
    }

    public static OrderItems of(List<OrderItem> values) {
        return new OrderItems(values);
    }

    public long calculateTotalPrice() {
        return values.stream()
            .mapToLong(OrderItem::getTotalPrice)
            .sum();
    }

    public Map<Long, Integer> quantitiesByProductId() {
        return values.stream()
            .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
    }
}
