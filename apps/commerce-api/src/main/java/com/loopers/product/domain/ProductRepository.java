package com.loopers.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    List<Product> findAllOrderByLatest();
    List<Product> findAllOrderByPriceAsc();
    int softDeleteByBrandId(Long brandId);
}
