package com.loopers.domain.order;

import java.util.List;
import java.util.stream.Collectors;

public record OrderLine(Long stockId, int quantity) {

    public static List<OrderLine> from(List<OrderItemInput> inputs) {
        return inputs.stream()
                .collect(Collectors.groupingBy(
                        OrderItemInput::stockId,
                        Collectors.summingInt(OrderItemInput::quantity)
                ))
                .entrySet().stream()
                .map(e -> new OrderLine(e.getKey(), e.getValue()))
                .toList();
    }
}
