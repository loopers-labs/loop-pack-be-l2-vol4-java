package com.loopers.infrastructure.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.order.OrderItemModel;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemModel, Long> {

    List<OrderItemModel> findByOrderIdAndDeletedAtIsNull(Long orderId);
}
