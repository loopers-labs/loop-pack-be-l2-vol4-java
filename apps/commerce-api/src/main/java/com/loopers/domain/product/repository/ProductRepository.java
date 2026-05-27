package com.loopers.domain.product.repository;

import com.loopers.domain.product.model.Product;

import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(Long id);
    Product save(Product product);
}
