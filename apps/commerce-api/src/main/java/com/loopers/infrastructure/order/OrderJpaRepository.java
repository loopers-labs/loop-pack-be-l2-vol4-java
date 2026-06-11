package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    @EntityGraph(attributePaths = "orderItems")  // EAGER Loading으로 orderItems도 함께 가져오기
    Optional<OrderModel> findByIdAndDeletedAtIsNull(Long id);
}