package com.loopers.domain.order;

import java.util.List;
import java.util.stream.Collectors;

public record OrderItemInput(Long stockId, int quantity) {

    public static List<OrderItemInput> merge(List<OrderItemInput> inputs) {
        return inputs.stream()
                .collect(Collectors.groupingBy(
                        OrderItemInput::stockId,
                        Collectors.summingInt(OrderItemInput::quantity)
                ))
                .entrySet().stream()
                .map(e -> new OrderItemInput(e.getKey(), e.getValue()))
                .toList();
    }
}
