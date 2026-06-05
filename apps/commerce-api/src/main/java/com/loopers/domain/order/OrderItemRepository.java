package com.loopers.domain.order;

import java.util.List;

public interface OrderItemRepository {
    List<OrderItem> saveAll(List<OrderItem> items);
    List<OrderItem> findAllByOrderId(Long orderId);
}
