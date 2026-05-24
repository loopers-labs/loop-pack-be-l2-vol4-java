package com.loopers.domain.brand;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeBrandRepository implements BrandRepository {

    private final Map<Long, BrandModel> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Optional<BrandModel> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public BrandModel save(BrandModel brand) {
        if (brand.getId() == null) {
            setId(brand, sequence.getAndIncrement());
        }
        store.put(brand.getId(), brand);
        return brand;
    }

    private void setId(BrandModel brand, long id) {
        try {
            var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(brand, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
