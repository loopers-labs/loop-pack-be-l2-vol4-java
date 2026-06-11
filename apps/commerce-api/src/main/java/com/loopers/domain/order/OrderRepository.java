package com.loopers.domain.order;

import java.util.Optional;

public interface OrderRepository {

    OrderModel save(OrderModel order);

    Optional<OrderModel> find(Long id);
}