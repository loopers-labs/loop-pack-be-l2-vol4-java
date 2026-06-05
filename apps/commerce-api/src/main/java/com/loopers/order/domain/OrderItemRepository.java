package com.loopers.order.domain;

import java.util.List;

public interface OrderItemRepository {
    OrderItem save(OrderItem orderItem);
    List<OrderItem> findByOrderId(Long orderId);
}
