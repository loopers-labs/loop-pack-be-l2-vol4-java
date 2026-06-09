package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItemModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemModel, Long> {
    List<OrderItemModel> findAllByOrderId(Long orderId);
}
