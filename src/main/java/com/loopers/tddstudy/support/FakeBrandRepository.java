package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.brand.BrandRepository;

import java.lang.reflect.Field;
import java.util.*;

public class FakeBrandRepository implements BrandRepository {

    private final Map<Long, Brand> store = new LinkedHashMap<>();
    private long sequence = 1L;

    @Override
    public Brand save(Brand brand) {
        if (getId(brand) == null) {
            setId(brand, sequence++);
        }
        store.put(getId(brand), brand);
        return brand;
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Brand> findAll() {
        return new ArrayList<>(store.values());
    }

    public void clear() {
        store.clear();
        sequence = 1L;
    }

    private Long getId(Brand brand) {
        try {
            Field field = Brand.class.getDeclaredField("id");
            field.setAccessible(true);
            return (Long) field.get(brand);
        } catch (Exception e) {
            throw new RuntimeException("Brand id 접근 실패", e);
        }
    }

    private void setId(Brand brand, Long id) {
        try {
            Field field = Brand.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(brand, id);
        } catch (Exception e) {
            throw new RuntimeException("Brand id 설정 실패", e);
        }
    }
}
