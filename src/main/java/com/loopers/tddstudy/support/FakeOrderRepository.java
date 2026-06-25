package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.order.Order;
import com.loopers.tddstudy.domain.order.OrderRepository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class FakeOrderRepository implements OrderRepository {

    private final Map<Long, Order> store = new LinkedHashMap<>();
    private long sequence = 1L;

    @Override
    public Order save(Order order) {
        if (getId(order) == null) {
            setId(order, sequence++);
        }
        store.put(getId(order), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findAllByUserId(Long userId) {
        return store.values().stream()
                .filter(o -> o.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }

    public void clear() {
        store.clear();
        sequence = 1L;
    }

    private Long getId(Order order) {
        try {
            Field field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            return (Long) field.get(order);
        } catch (Exception e) {
            throw new RuntimeException("Order id 접근 실패", e);
        }
    }

    private void setId(Order order, Long id) {
        try {
            Field field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException("Order id 설정 실패", e);
        }
    }


    @Override
    public List<Order> findAllByStatus(String status) {
        return store.values().stream()
                .filter(order -> status.equals(order.getStatus()))
                .toList();
    }

}
