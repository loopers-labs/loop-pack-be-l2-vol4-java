package com.loopers.domain.coupon;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeCouponTemplateRepository implements CouponTemplateRepository {

    private final Map<Long, CouponTemplate> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public CouponTemplate save(CouponTemplate template) {
        if (template.getId() == null || template.getId() == 0L) {
            assignId(template, sequence.incrementAndGet());
        }
        store.put(template.getId(), template);
        return template;
    }

    @Override
    public Optional<CouponTemplate> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<CouponTemplate> findAll(int page, int size) {
        List<CouponTemplate> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(CouponTemplate::getId).reversed());
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }

    static void assignId(Object entity, Long id) {
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
