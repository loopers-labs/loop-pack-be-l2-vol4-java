package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class InMemoryBrandRepository implements BrandRepository {

    private final List<BrandModel> store = new ArrayList<>();

    @Override
    public BrandModel save(BrandModel brand) {
        store.add(brand);
        return brand;
    }

    @Override
    public Optional<BrandModel> findById(Long id) {
        return store.stream()
                .filter(b -> b.getId().equals(id))
                .findFirst();
    }

    @Override
    public boolean existsByName(String name) {
        return store.stream().anyMatch(b -> b.getName().equals(name));
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return store.stream().anyMatch(b -> b.getName().equals(name));
    }

    @Override
    public Page<BrandModel> findAll(Pageable pageable) {
        return new PageImpl<>(store, pageable, store.size());
    }

    @Override
    public List<BrandModel> findAllByIds(List<Long> ids) {
        return store.stream()
                .filter(b -> ids.contains(b.getId()))
                .toList();
    }
}
