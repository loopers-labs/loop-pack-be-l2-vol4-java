package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findActiveById(Long id);
    Page<ProductModel> findAllActive(Pageable pageable, Long brandId);
    List<ProductModel> findAllByBrandId(Long brandId);
}
