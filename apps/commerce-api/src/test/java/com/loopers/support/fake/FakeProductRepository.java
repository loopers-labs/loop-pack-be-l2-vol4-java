package com.loopers.support.fake;

import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class FakeProductRepository implements ProductRepository {

    private final Map<Long, ProductModel> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public ProductModel save(ProductModel product) {
        if (product.getId() == null || product.getId() == 0L) {
            IdFixtures.assignId(product, seq.incrementAndGet());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return Optional.ofNullable(store.get(id)).filter(p -> p.getDeletedAt() == null);
    }

    @Override
    public List<ProductModel> findAll() {
        return store.values().stream().filter(p -> p.getDeletedAt() == null).toList();
    }

    @Override
    public List<ProductModel> findByBrandId(Long brandId) {
        return store.values().stream()
            .filter(p -> p.getDeletedAt() == null && p.getBrandId().equals(brandId))
            .toList();
    }

    @Override
    public List<ProductModel> findAllByIds(Collection<Long> ids) {
        return ids.stream()
            .map(store::get)
            .filter(p -> p != null && p.getDeletedAt() == null)
            .toList();
    }
}
