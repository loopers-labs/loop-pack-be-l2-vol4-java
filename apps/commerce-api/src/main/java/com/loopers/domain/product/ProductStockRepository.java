package com.loopers.domain.product;

import java.util.Optional;

public interface ProductStockRepository {

    ProductStockModel save(ProductStockModel stock);

    Optional<ProductStockModel> findByProductId(Long productId);

    Optional<ProductStockModel> findByProductIdForUpdate(Long productId);
}