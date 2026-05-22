package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> find(Long id);
    List<Product> findAll();
    void delete(Long id);
}
