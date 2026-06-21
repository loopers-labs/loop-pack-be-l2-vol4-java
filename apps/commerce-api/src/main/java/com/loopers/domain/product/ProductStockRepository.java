package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductStockRepository {
    ProductStockModel save(ProductStockModel stock);
    Optional<ProductStockModel> findById(Long id);
    List<ProductStockModel> findAllByProductId(Long productId);
    boolean decreaseIfSufficient(Long stockId, int quantity);
    void increaseStock(Long stockId, int quantity);
    boolean updateStockAttributes(Long productId, Long stockId, Long price, Integer delta);
}
