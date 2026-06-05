package com.loopers.order.infrastructure;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderJpaRepository.findByUserIdOrderByOrderedAtDesc(userId);
    }

    @Override
    public List<Order> findAll() {
        return orderJpaRepository.findAllByOrderByOrderedAtDesc();
    }
}
