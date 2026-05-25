package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(Long id);
    List<Product> findAll(Long brandId, String sort, int page, int size);
    long count(Long brandId);
}
