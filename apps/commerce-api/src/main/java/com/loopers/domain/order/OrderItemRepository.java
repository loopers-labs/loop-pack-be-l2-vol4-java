package com.loopers.domain.order;

import java.util.List;

public interface OrderItemRepository {
    OrderItemModel save(OrderItemModel orderItem);
    List<OrderItemModel> saveAll(List<OrderItemModel> orderItems);
    List<OrderItemModel> findAllByOrderId(Long orderId);
}
