package com.loopers.domain.product;

import java.util.Optional;

public interface ProductStockRepository {
    ProductStock save(ProductStock stock);
    Optional<ProductStock> findByProductId(Long productId);
}
