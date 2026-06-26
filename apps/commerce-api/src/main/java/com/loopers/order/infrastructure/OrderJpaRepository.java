package com.loopers.order.infrastructure;

import com.loopers.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByOrderedAtDesc(Long userId);
    List<Order> findAllByOrderByOrderedAtDesc();
}
