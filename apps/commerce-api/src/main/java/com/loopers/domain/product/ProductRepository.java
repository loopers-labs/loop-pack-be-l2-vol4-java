package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    List<ProductModel> findAll(ProductSortType sort, int page, int size);
    void delete(Long id);
}
