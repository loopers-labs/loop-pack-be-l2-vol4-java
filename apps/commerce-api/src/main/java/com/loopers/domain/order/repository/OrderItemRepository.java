package com.loopers.domain.order.repository;

import com.loopers.domain.order.model.OrderItem;

import java.util.List;

public interface OrderItemRepository {
    List<OrderItem> saveAll(List<OrderItem> items);
    List<OrderItem> findAllByOrderId(Long orderId);
}
