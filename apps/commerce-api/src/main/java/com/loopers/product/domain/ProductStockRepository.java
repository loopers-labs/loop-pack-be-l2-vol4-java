package com.loopers.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductStockRepository {
    ProductStock save(ProductStock stock);
    Optional<ProductStock> findByProductId(Long productId);
    Optional<ProductStock> findByProductIdForUpdate(Long productId);
    int softDeleteByBrandId(Long brandId);
}
