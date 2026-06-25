package com.loopers.tddstudy.infrastructure.order;

import com.loopers.tddstudy.domain.order.Order;
import com.loopers.tddstudy.domain.order.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        return jpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Order> findAllByUserId(Long userId) {
        return jpaRepository.findAllByUserId(userId);
    }

    @Override
    public List<Order> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<Order> findAllByStatus(String status) {
        return jpaRepository.findAllByStatus(status);
    }



}
