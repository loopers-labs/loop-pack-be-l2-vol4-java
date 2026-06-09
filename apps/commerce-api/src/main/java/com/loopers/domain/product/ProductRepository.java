package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    Optional<ProductModel> findWithLock(Long id);
    List<ProductModel> findAll();
    List<ProductModel> findAllActive(Long brandId);
    void delete(Long id);
}
