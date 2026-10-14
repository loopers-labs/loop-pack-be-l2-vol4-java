package com.loopers.domain.brand;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 단위 테스트용 인메모리 BrandRepository.
 * BaseEntity.id 는 final 이므로 reflection 으로 주입한다 (도메인 의존성 제거 목적).
 */
public class FakeBrandRepository implements BrandRepository {

    private final Map<Long, BrandModel> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public BrandModel save(BrandModel brand) {
        if (brand.getId() == null || brand.getId() == 0L) {
            assignId(brand, sequence.incrementAndGet());
        }
        store.put(brand.getId(), brand);
        return brand;
    }

    @Override
    public Optional<BrandModel> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    private static void assignId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && !clazz.getSimpleName().equals("BaseEntity")) {
                clazz = clazz.getSuperclass();
            }
            if (clazz == null) {
                throw new IllegalStateException("BaseEntity 를 찾을 수 없습니다.");
            }
            Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Fake Repository: ID 주입 실패", e);
        }
    }
}
