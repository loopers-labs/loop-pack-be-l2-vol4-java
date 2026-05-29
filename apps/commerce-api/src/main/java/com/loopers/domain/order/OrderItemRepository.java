package com.loopers.domain.order;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository {
    List<OrderItem> saveAll(List<OrderItem> items);
    List<OrderItem> findByOrderId(Long orderId);
    List<OrderItem> findByOrderIdIn(Collection<Long> orderIds);
}
