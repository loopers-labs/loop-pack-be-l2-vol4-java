package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);
    Optional<ProductModel> findByIdForUpdate(Long id);
    Page<ProductModel> findAll(Long brandId, ProductSort sort, PageRequest pageRequest);
    List<ProductModel> findAllByBrandId(Long brandId);
}
