package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {
    Optional<OrderModel> findByIdAndDeletedAtIsNull(Long id);
}
