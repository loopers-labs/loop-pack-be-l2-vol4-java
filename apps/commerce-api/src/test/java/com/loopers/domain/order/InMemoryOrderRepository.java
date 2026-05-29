package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

class InMemoryOrderRepository implements OrderRepository {

    private final Map<Long, OrderModel> store = new HashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @Override
    public OrderModel save(OrderModel order) {
        store.put(idSequence.getAndIncrement(), order);
        return order;
    }

    @Override
    public Optional<OrderModel> findById(Long id) {
        return store.values().stream()
                .filter(o -> o.getId().equals(id))
                .findFirst();
    }

    @Override
    public Optional<OrderModel> findByOrderNumber(String orderNumber) {
        return store.values().stream()
                .filter(o -> o.getOrderNumber().equals(orderNumber))
                .findFirst();
    }

    @Override
    public List<OrderModel> findAllByUserId(Long userId) {
        return store.values().stream()
                .filter(o -> o.getUserId().equals(userId))
                .toList();
    }

    @Override
    public List<OrderModel> findAllByUserIdWithDateRange(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return findAllByUserId(userId);
    }

    @Override
    public Page<OrderModel> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }
}
