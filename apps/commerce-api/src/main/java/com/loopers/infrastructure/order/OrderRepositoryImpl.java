package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<OrderModel> find(Long id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public Optional<OrderModel> findByIdAndUserLoginId(Long id, String userLoginId) {
        return orderJpaRepository.findByIdAndUserLoginId(id, userLoginId);
    }

    @Override
    public List<OrderModel> findAll() {
        return orderJpaRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<OrderModel> findAll(int page, int size) {
        return orderJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Override
    public List<OrderModel> findAllByUserLoginId(String userLoginId) {
        return orderJpaRepository.findAllByUserLoginIdOrderByCreatedAtDesc(userLoginId);
    }
}
