package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public OrderItemModel saveItem(OrderItemModel item) {
        return orderItemJpaRepository.save(item);
    }

    @Override
    public Optional<OrderModel> findById(Long id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public Optional<OrderModel> findByIdForUpdate(Long id) {
        return orderJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public List<OrderModel> findAllByMemberIdAndDateRange(Long memberId, LocalDate startAt, LocalDate endAt) {
        ZonedDateTime start = startAt != null ? startAt.atStartOfDay(ZoneOffset.UTC) : LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime end = endAt != null ? endAt.plusDays(1).atStartOfDay(ZoneOffset.UTC) : LocalDate.of(2100, 1, 1).atStartOfDay(ZoneOffset.UTC);
        return orderJpaRepository.findAllByMemberIdAndCreatedAtBetween(memberId, start, end);
    }

    @Override
    public List<OrderModel> findAllByDateRange(LocalDate startAt, LocalDate endAt) {
        ZonedDateTime start = startAt != null ? startAt.atStartOfDay(ZoneOffset.UTC) : LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime end = endAt != null ? endAt.plusDays(1).atStartOfDay(ZoneOffset.UTC) : LocalDate.of(2100, 1, 1).atStartOfDay(ZoneOffset.UTC);
        return orderJpaRepository.findAllByCreatedAtBetween(start, end);
    }

    @Override
    public List<OrderItemModel> findItemsByOrderId(Long orderId) {
        return orderItemJpaRepository.findAllByOrderId(orderId);
    }

    @Override
    public List<OrderItemModel> findItemsByOrderIdIn(List<Long> orderIds) {
        return orderItemJpaRepository.findAllByOrderIdIn(orderIds);
    }
}
