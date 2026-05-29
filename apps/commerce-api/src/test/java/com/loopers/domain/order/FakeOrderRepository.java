package com.loopers.domain.order;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 단위 테스트용 인메모리 OrderRepository.
 */
public class FakeOrderRepository implements OrderRepository {

    private final Map<Long, Order> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public Order save(Order order) {
        if (order.getId() == null || order.getId() == 0L) {
            ReflectionTestUtils.setField(order, "id", sequence.incrementAndGet());
        }
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        List<Order> result = new ArrayList<>();
        for (Order order : store.values()) {
            if (order.getUserId().equals(userId)) {
                result.add(order);
            }
        }
        return result;
    }

    @Override
    public List<Order> findByUserIdAndPeriod(Long userId, ZonedDateTime start, ZonedDateTime end) {
        List<Order> result = new ArrayList<>();
        for (Order order : store.values()) {
            if (!order.getUserId().equals(userId)) continue;
            ZonedDateTime createdAt = order.getCreatedAt();
            if (createdAt == null) continue; // 단위테스트 환경에선 @PrePersist 미실행이라 null 일 수 있음
            if (!createdAt.isBefore(start) && !createdAt.isAfter(end)) {
                result.add(order);
            }
        }
        return result;
    }
}
