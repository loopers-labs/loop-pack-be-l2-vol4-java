package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    @EntityGraph(attributePaths = {"items"})
    Optional<OrderModel> findById(Long id);

    Optional<OrderModel> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items"})
    List<OrderModel> findAllByUserId(Long userId);

    @Override
    @EntityGraph(attributePaths = {"items"})
    Page<OrderModel> findAll(Pageable pageable);
}