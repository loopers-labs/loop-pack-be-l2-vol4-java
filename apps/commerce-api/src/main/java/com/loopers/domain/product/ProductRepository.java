package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    Optional<ProductModel> findActive(Long id);
    List<ProductModel> findAll(ProductSortType sort, int page, int size);
    List<ProductModel> findAllActiveByBrandId(Long brandId);
    void delete(Long id);
}
