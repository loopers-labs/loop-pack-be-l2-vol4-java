package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    List<ProductModel> findAllByIds(List<Long> ids);
    List<ProductModel> findAll();
    List<ProductModel> findAll(ProductSort sort);
    List<ProductModel> findAll(ProductSort sort, int page, int size);
    List<ProductModel> findAllByBrandId(Long brandId);
    List<ProductModel> findAllByBrandId(Long brandId, ProductSort sort);
    List<ProductModel> findAllByBrandId(Long brandId, ProductSort sort, int page, int size);
}
