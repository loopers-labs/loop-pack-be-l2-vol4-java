package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

public interface OrderRepository {

    OrderModel save(OrderModel order, List<OrderItemModel> orderItems);

    OrderModel getActiveById(Long orderId);

    OrderModel getActiveByIdAndUserId(Long orderId, Long userId);

    Page<OrderModel> findActiveByUserIdAndOrderedAtBetween(Long userId, ZonedDateTime startInclusive, ZonedDateTime endExclusive, int page, int size);

    Page<OrderModel> findActiveByPage(int page, int size);

    List<OrderItemModel> findActiveItemsByOrderId(Long orderId);
}
