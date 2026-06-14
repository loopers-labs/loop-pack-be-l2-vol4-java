package com.loopers.order.domain;

import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long orderId);

    PageResult<Order> findAllByUserId(Long userId, PageQuery query, ZonedDateTime startAt, ZonedDateTime endBefore);

    PageResult<Order> findAll(PageQuery query);
}
