package com.loopers.support.fake;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class FakeProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public Product save(Product product) {
        if (product.getId() == null || product.getId() == 0L) {
            IdFixtures.assignId(product, seq.incrementAndGet());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> find(Long id) {
        return Optional.ofNullable(store.get(id)).filter(p -> p.getDeletedAt() == null);
    }

    @Override
    public List<Product> findAll() {
        return store.values().stream().filter(p -> p.getDeletedAt() == null).toList();
    }

    @Override
    public List<Product> findByBrandId(Long brandId) {
        return store.values().stream()
            .filter(p -> p.getDeletedAt() == null && p.getBrandId().equals(brandId))
            .toList();
    }

    @Override
    public List<Product> findAllByIds(Collection<Long> ids) {
        return ids.stream()
            .map(store::get)
            .filter(p -> p != null && p.getDeletedAt() == null)
            .toList();
    }
}
