package com.loopers.domain.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class InMemoryProductStockRepository implements ProductStockRepository {

    private final List<ProductStockModel> store = new ArrayList<>();

    @Override
    public ProductStockModel save(ProductStockModel stock) {
        store.add(stock);
        return stock;
    }

    @Override
    public Optional<ProductStockModel> findById(Long id) {
        return store.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<ProductStockModel> findAllByProductId(Long productId) {
        return store.stream()
                .filter(s -> s.getProduct().getId().equals(productId))
                .toList();
    }

    @Override
    public boolean decreaseIfSufficient(Long stockId, int quantity) {
        return false;
    }

    @Override
    public void increaseStock(Long stockId, int quantity) {
    }

    @Override
    public boolean updateStockAttributes(Long stockId, Long price, Integer delta) {
        return false;
    }
}
