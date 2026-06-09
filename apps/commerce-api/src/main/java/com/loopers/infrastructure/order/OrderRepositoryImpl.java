package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
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
    public Optional<OrderModel> find(Long orderId) {
        return orderJpaRepository.findByIdAndDeletedAtIsNull(orderId);
    }

    @Override
    public List<OrderModel> findAllByMemberId(Long memberId) {
        return orderJpaRepository.findAllByMemberIdAndDeletedAtIsNull(memberId);
    }

    @Override
    public List<OrderModel> findAllByMemberIdAndCreatedAtBetween(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderJpaRepository.findAllByMemberIdAndCreatedAtBetweenAndDeletedAtIsNull(memberId, startAt, endAt);
    }
}
