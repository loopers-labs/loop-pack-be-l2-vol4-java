package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
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
    public Optional<OrderModel> findById(Long id) {
        return orderJpaRepository.findWithItemsById(id);
    }

    @Override
    public List<OrderModel> findByUserIdAndOrderedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderJpaRepository.findByUserIdAndOrderedAtBetween(userId, startAt, endAt);
    }

    @Override
    public List<OrderModel> findAll(int page, int size) {
        return orderJpaRepository.findAll(PageRequest.of(page, size)).getContent();
    }
}
