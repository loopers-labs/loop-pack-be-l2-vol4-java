package com.loopers.infrastructure.order;

import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public Page<Order> findAllByMemberId(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        return orderJpaRepository.findAllByMemberId(memberId, startAt, endAt, pageable);
    }
}
