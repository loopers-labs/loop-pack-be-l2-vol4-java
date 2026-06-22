package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import java.util.Optional;
import java.util.List;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> findById(Long id);
    List<OrderModel> findAllByUserId(Long userId);
    List<OrderModel> findAll();
}
