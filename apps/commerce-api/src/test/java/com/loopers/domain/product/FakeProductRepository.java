package com.loopers.domain.product;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 단위 테스트용 인메모리 ProductRepository.
 */
public class FakeProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public Product save(Product product) {
        if (product.getId() == null || product.getId() == 0L) {
            ReflectionTestUtils.setField(product, "id", sequence.incrementAndGet());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> find(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Product> findAll(Long brandId, ProductSortType sort, int page, int size) {
        List<Product> result = new ArrayList<>(store.values());
        if (brandId != null) {
            result.removeIf(p -> !brandId.equals(p.getBrandId()));
        }
        switch (sort) {
            case PRICE_ASC -> result.sort(Comparator.comparing(p -> p.getPrice().amount()));
            // LIKES_DESC 는 실제 구현체에서 JPQL JOIN 으로 처리. Fake 는 좋아요 정보 없어 LATEST 와 동일하게 fallback.
            default -> result.sort(Comparator.comparing(Product::getId).reversed());
        }
        int from = Math.min(page * size, result.size());
        int to = Math.min(from + size, result.size());
        return result.subList(from, to);
    }

    @Override
    public List<Product> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Product> result = new ArrayList<>();
        for (Long id : ids) {
            Product product = store.get(id);
            if (product != null) {
                result.add(product);
            }
        }
        return result;
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
