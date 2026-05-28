package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {

    ProductModel save(ProductModel product);

    Optional<ProductModel> find(Long id);

    Page<ProductModel> search(Long brandId, ProductSortType sort, Pageable pageable);
}