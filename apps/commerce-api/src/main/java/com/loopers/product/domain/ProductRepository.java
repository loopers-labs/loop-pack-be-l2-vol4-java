package com.loopers.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    Optional<Product> findActiveById(Long id);
    boolean existsActiveById(Long id);
    List<Product> findAllOnSale(Long brandId, ProductSortOption sort, long offset, int limit);
    long countOnSale(Long brandId);
    List<Product> findAllOrderByLatest();
    List<Product> findAllByIdIn(List<Long> ids);
    int softDeleteByBrandId(Long brandId);
    int incrementLikeCount(Long productId, long delta);
    int reconcileLikeCounts();
}
