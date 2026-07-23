package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    ProductModel save(ProductModel product);

    Optional<ProductModel> find(Long id);

    List<ProductModel> findAllByIdIn(List<Long> ids);

    Page<ProductModel> search(Long brandId, ProductStatus status, ProductSortType sort, Pageable pageable);

    void increaseLikeCount(Long productId);

    void decreaseLikeCount(Long productId);
}