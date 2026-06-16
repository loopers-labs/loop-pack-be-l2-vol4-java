package com.loopers.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    Optional<Product> findActiveById(Long id);
    boolean existsActiveById(Long id);
    List<Product> findAllOnSale(ProductSortOption sort);
    List<Product> findAllOrderByLatest();
    List<Product> findAllByIdIn(List<Long> ids);
    int softDeleteByBrandId(Long brandId);
}
