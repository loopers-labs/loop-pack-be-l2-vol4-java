package com.loopers.tddstudy.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findAllByUserId(Long userId);

    List<Order> findAll();

    List<Order> findAllByStatus(String status);
}
