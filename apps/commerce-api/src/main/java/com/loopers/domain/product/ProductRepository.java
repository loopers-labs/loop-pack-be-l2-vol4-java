package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(UUID id);
    List<ProductModel> findAll();
    void delete(UUID id);
}
