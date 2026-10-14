package com.loopers.domain.order;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeOrderRepository implements OrderRepository {

    private final Map<Long, OrderModel> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public OrderModel save(OrderModel order) {
        if (order.getId() == null || order.getId() == 0L) {
            assignId(order, sequence.incrementAndGet());
        }
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<OrderModel> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<OrderModel> findAllByUserId(Long userId) {
        List<OrderModel> result = new ArrayList<>();
        for (OrderModel o : store.values()) {
            if (o.getUserId().equals(userId)) {
                result.add(o);
            }
        }
        return result;
    }

    private static void assignId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && !clazz.getSimpleName().equals("BaseEntity")) {
                clazz = clazz.getSuperclass();
            }
            Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Fake Repository: ID 주입 실패", e);
        }
    }
}
