package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> find(Long id);
    List<Product> findAllByIds(List<Long> ids);
    List<Product> findAll(ProductSort sort, int page, int size);
    List<Product> findAllByBrandId(Long brandId);
    List<Product> findAllByBrandId(Long brandId, ProductSort sort, int page, int size);
}
