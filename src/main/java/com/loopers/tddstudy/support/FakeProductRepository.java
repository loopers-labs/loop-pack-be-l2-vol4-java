package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class FakeProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new LinkedHashMap<>();
    private long sequence = 1L;

    @Override
    public Product save(Product product) {
        if (getId(product) == null) {
            setId(product, sequence++);
        }
        store.put(getId(product), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        return findById(id); // 단위 테스트에서는 락 없이 동일하게 동작
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return store.values().stream()
                .filter(p -> brandId.equals(p.getBrandId()))
                .collect(Collectors.toList());
    }

    public void clear() {
        store.clear();
        sequence = 1L;
    }

    private Long getId(Product product) {
        try {
            Field field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            return (Long) field.get(product);
        } catch (Exception e) {
            throw new RuntimeException("Product id 접근 실패", e);
        }
    }

    private void setId(Product product, Long id) {
        try {
            Field field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (Exception e) {
            throw new RuntimeException("Product id 설정 실패", e);
        }
    }
}
