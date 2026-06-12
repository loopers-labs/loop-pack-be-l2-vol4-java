package com.loopers.tddstudy.infrastructure.order;

import com.loopers.tddstudy.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByUserId(Long userId);
}
