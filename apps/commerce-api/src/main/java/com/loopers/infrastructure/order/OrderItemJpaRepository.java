package com.loopers.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemJpaEntity, Long> {
    List<OrderItemJpaEntity> findAllByOrderIdAndDeletedAtIsNull(Long orderId);
}
