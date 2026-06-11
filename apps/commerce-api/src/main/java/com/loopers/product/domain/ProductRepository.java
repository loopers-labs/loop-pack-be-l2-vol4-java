package com.loopers.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    Optional<Product> findActiveById(Long id);
    List<Product> findAllOnSaleOrderByLatest();
    List<Product> findAllOnSaleOrderByPriceAsc();
    List<Product> findAllOnSaleOrderByLikeCountDesc();
    List<Product> findAllOrderByLatest();
    List<Product> findAllByIdIn(List<Long> ids);
    int softDeleteByBrandId(Long brandId);
}
