package com.loopers.order.infrastructure;

import com.loopers.order.domain.OrderModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {
    List<OrderModel> findByMemberId(Long memberId);
}
