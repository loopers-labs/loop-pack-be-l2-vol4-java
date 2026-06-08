package com.loopers.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    OrderItemModel saveItem(OrderItemModel item);
    Optional<OrderModel> find(Long id);
    List<OrderItemModel> findItemsByOrderId(Long orderId);
}
