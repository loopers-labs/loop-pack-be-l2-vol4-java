package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    public List<Order> findByUserId(Long userId, String startAt, String endAt) {
        LocalDateTime start = startAt != null ? LocalDate.parse(startAt).atStartOfDay() : null;
        LocalDateTime end = endAt != null ? LocalDate.parse(endAt).atTime(23, 59, 59) : null;
        return orderJpaRepository.findByUserIdAndDateRange(userId, start, end);
    }
}
