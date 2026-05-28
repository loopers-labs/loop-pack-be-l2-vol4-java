package com.loopers.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> find(Long id);
    Optional<Order> findByIdAndUserLoginId(Long id, String userLoginId);
    List<Order> findAll();
    List<Order> findAll(int page, int size);
    List<Order> findAllByUserLoginId(String userLoginId);
}
