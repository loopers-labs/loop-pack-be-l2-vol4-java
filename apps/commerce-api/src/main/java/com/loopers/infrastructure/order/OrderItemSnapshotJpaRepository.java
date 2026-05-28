package com.loopers.infrastructure.order;

import com.loopers.domain.order.model.OrderItemSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemSnapshotJpaRepository extends JpaRepository<OrderItemSnapshot, Long> {
}
