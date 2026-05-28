package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductEntity save(ProductEntity product);
    Optional<ProductEntity> find(Long id);
    List<ProductEntity> findAll();
}
