package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);

    Optional<ProductModel> findById(Long id);

    /**
     * 상품 목록 조회. brandId 가 null 이면 전체, 아니면 해당 브랜드 상품만.
     */
    List<ProductModel> findAll(Long brandId, ProductSortOption sort, int page, int size);
}
