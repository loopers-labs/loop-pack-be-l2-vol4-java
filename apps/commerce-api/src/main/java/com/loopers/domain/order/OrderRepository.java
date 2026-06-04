package com.loopers.domain.order;

import com.loopers.domain.common.PageResult;

import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> find(Long id);

    PageResult<Order> findAllByUser(OrderCommand.MySearch search);

    PageResult<Order> findAll(OrderCommand.AdminSearch search);
}
