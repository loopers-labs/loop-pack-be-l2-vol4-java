package com.loopers.infrastructure.order;

import com.loopers.domain.order.model.OrderItemSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemSnapshotJpaRepository extends JpaRepository<OrderItemSnapshot, Long> {
    List<OrderItemSnapshot> findAllByOrderItemIdIn(List<Long> orderItemIds);
}
