package com.loopers.domain.order;

import java.util.List;

public record OrderResult(OrderModel order, List<OrderItem> items) {
}
