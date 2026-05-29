package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    List<ProductModel> findAll(SortType sortType);
    void delete(Long id);
    void increaseLikeCount(Long productId);
    void decreaseLikeCount(Long productId);
    void deleteAllByBrandId(Long brandId);
}
