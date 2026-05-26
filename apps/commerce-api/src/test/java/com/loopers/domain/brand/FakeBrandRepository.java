package com.loopers.domain.brand;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 단위 테스트용 인메모리 BrandRepository.
 */
public class FakeBrandRepository implements BrandRepository {

    private final Map<Long, Brand> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public Brand save(Brand brand) {
        if (brand.getId() == null || brand.getId() == 0L) {
            ReflectionTestUtils.setField(brand, "id", sequence.incrementAndGet());
        }
        store.put(brand.getId(), brand);
        return brand;
    }

    @Override
    public Optional<Brand> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Brand> findAll(int page, int size) {
        List<Brand> all = new ArrayList<>(store.values());
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && store.containsKey(id);
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }
}
