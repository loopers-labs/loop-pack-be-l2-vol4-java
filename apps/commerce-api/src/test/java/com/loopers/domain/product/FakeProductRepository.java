package com.loopers.domain.product;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeProductRepository implements ProductRepository {

    private final Map<Long, ProductModel> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public ProductModel save(ProductModel product) {
        if (product.getId() == null || product.getId() == 0L) {
            assignId(product, sequence.incrementAndGet());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    @Override
    public List<ProductModel> findAll(ProductSortType sortType, Long brandId) {
        List<ProductModel> filtered = new ArrayList<>();
        for (ProductModel p : store.values()) {
            if (brandId == null || brandId.equals(p.getBrandId())) {
                filtered.add(p);
            }
        }
        Comparator<ProductModel> comparator = switch (sortType) {
            case LATEST -> Comparator.comparing(ProductModel::getId, Comparator.reverseOrder());
            case PRICE_ASC -> Comparator.comparing(ProductModel::getPrice);
            case LIKES_DESC -> Comparator.comparing(ProductModel::getLikeCount, Comparator.reverseOrder());
        };
        filtered.sort(comparator);
        return filtered;
    }

    @Override
    public void incrementLikeCount(Long productId) {
        ProductModel product = store.get(productId);
        if (product != null) {
            product.incrementLikeCount();
        }
    }

    @Override
    public void decrementLikeCount(Long productId) {
        ProductModel product = store.get(productId);
        if (product != null) {
            product.decrementLikeCount();
        }
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
