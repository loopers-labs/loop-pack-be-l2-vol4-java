package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);
    List<ProductModel> findAllByIds(List<Long> ids);
    List<ProductModel> findAll(Long brandId, String sort, int page, int size);
    List<ProductModel> findAllByBrandId(Long brandId);
}
