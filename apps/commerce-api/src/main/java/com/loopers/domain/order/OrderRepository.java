package com.loopers.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> find(Long id);
    Optional<OrderModel> findByIdAndUserLoginId(Long id, String userLoginId);
    List<OrderModel> findAll();
    List<OrderModel> findAll(int page, int size);
    List<OrderModel> findAllByUserLoginId(String userLoginId);
}
