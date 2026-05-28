package com.loopers.domain.order.repository;

import com.loopers.domain.order.model.OrderItemSnapshot;

import java.util.List;

public interface OrderItemSnapshotRepository {
    List<OrderItemSnapshot> saveAll(List<OrderItemSnapshot> snapshots);
    List<OrderItemSnapshot> findAllByOrderItemIdIn(List<Long> orderItemIds);
}
