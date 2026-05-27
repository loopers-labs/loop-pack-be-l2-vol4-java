package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {
    Optional<OrderModel> findByIdAndUserLoginId(Long id, String userLoginId);

    List<OrderModel> findAllByOrderByCreatedAtDesc();

    List<OrderModel> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<OrderModel> findAllByUserLoginIdOrderByCreatedAtDesc(String userLoginId);
}
