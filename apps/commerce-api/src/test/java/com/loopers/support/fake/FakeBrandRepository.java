package com.loopers.support.fake;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class FakeBrandRepository implements BrandRepository {

    private final Map<Long, Brand> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public Brand save(Brand brand) {
        if (brand.getId() == null || brand.getId() == 0L) {
            IdFixtures.assignId(brand, seq.incrementAndGet());
        }
        store.put(brand.getId(), brand);
        return brand;
    }

    @Override
    public Optional<Brand> find(Long id) {
        return Optional.ofNullable(store.get(id)).filter(b -> b.getDeletedAt() == null);
    }

    @Override
    public List<Brand> findAll() {
        return store.values().stream().filter(b -> b.getDeletedAt() == null).toList();
    }

    @Override
    public List<Brand> findAllByIds(Collection<Long> ids) {
        return ids.stream()
            .map(store::get)
            .filter(b -> b != null && b.getDeletedAt() == null)
            .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return find(id).isPresent();
    }
}
