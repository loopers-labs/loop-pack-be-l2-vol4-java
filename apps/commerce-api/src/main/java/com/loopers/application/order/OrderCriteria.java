package com.loopers.application.order;

import com.loopers.domain.order.OrderLine;

import java.util.List;

public final class OrderCriteria {

    private OrderCriteria() {}

    public record Create(Long userId, List<OrderLine> lines) {}
}
