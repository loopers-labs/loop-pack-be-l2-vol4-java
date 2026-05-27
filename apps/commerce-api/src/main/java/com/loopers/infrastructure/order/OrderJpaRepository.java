package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<OrderEntity> findAllByOrderByIdDesc(Pageable pageable);

    List<OrderEntity> findByStatusOrderByIdDesc(OrderStatus status, Pageable pageable);
}
