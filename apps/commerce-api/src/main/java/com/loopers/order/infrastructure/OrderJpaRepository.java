package com.loopers.order.infrastructure;

import com.loopers.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByUserIdOrderByOrderedAtDesc(Long userId);
    List<Order> findAllByOrderByOrderedAtDesc();
}
