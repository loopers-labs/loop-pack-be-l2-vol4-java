package com.loopers.support.fake;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.domain.BrandRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class FakeBrandRepository implements BrandRepository {

    private final Map<Long, BrandModel> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public BrandModel save(BrandModel brand) {
        if (brand.getId() == null || brand.getId() == 0L) {
            IdFixtures.assignId(brand, seq.incrementAndGet());
        }
        store.put(brand.getId(), brand);
        return brand;
    }

    @Override
    public Optional<BrandModel> find(Long id) {
        return Optional.ofNullable(store.get(id)).filter(b -> b.getDeletedAt() == null);
    }

    @Override
    public List<BrandModel> findAll() {
        return store.values().stream().filter(b -> b.getDeletedAt() == null).toList();
    }

    @Override
    public List<BrandModel> findAllByIds(Collection<Long> ids) {
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
