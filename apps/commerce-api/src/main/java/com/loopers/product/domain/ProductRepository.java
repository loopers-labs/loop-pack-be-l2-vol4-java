package com.loopers.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    List<ProductModel> findAll();
    void delete(Long id);
}
