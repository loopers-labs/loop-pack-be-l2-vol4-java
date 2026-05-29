package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductStockRepository {
    ProductStockModel save(ProductStockModel stock);
    Optional<ProductStockModel> findById(Long id);
    Optional<ProductStockModel> findByIdForUpdate(Long id);
//    List<ProductStockModel> findAllByIds(List<Long> ids);
    List<ProductStockModel> findAllByProductId(Long productId);
}
