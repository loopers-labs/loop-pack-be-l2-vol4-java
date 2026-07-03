package com.loopers.domain.metrics;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeProductMetricsRepository implements ProductMetricsRepository {

    private final List<ProductMetrics> store = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public ProductMetrics save(ProductMetrics metrics) {
        if (metrics.getId() == null || metrics.getId() == 0L) {
            assignId(metrics, sequence.incrementAndGet());
            store.add(metrics);
        }
        return metrics;
    }

    @Override
    public Optional<ProductMetrics> findByProductId(Long productId) {
        return store.stream().filter(m -> m.getProductId().equals(productId)).findFirst();
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
