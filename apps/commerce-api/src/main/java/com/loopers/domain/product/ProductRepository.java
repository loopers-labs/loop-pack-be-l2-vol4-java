package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);
    Page<ProductModel> findAll(Long brandId, ProductSortType sort, Pageable pageable);
    void delete(Long id);
    boolean existsById(Long id);
}
