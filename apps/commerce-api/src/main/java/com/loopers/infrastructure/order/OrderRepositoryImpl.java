package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
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
    public Order save(Order order) {
        OrderJpaEntity orderJpaEntity = order.getId() == null
            ? OrderJpaEntity.from(order)
            : orderJpaRepository.findById(order.getId())
                .map(existingOrder -> {
                    existingOrder.update(order);
                    return existingOrder;
                })
                .orElseGet(() -> OrderJpaEntity.from(order));

        return orderJpaRepository.save(orderJpaEntity).toDomain();
    }

    @Override
    public Optional<Order> find(Long id) {
        return orderJpaRepository.findById(id)
            .map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByIdAndUserLoginId(Long id, String userLoginId) {
        return orderJpaRepository.findByIdAndUserLoginId(id, userLoginId)
            .map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findAll() {
        return orderJpaRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(OrderJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Order> findAll(int page, int size) {
        return orderJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)).stream()
            .map(OrderJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Order> findAllByUserLoginId(String userLoginId) {
        return orderJpaRepository.findAllByUserLoginIdOrderByCreatedAtDesc(userLoginId).stream()
            .map(OrderJpaEntity::toDomain)
            .toList();
    }
}
