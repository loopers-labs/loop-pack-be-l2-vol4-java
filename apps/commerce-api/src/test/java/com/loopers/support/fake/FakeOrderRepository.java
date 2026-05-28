package com.loopers.support.fake;

import com.loopers.order.domain.OrderModel;
import com.loopers.order.domain.OrderRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeOrderRepository implements OrderRepository {

    private final Map<Long, OrderModel> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public OrderModel save(OrderModel order) {
        if (order.getId() == null || order.getId() == 0L) {
            IdFixtures.assignId(order, seq.incrementAndGet());
        }
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<OrderModel> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<OrderModel> findByMemberId(Long memberId) {
        return store.values().stream().filter(o -> o.getMemberId().equals(memberId)).toList();
    }

    @Override
    public List<OrderModel> findAll() {
        return store.values().stream().toList();
    }
}
