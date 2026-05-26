package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    List<ProductModel> findAll();

    /** 특정 브랜드의 활성 상품 전체 — Brand 비활성 시 cascade 전파용 (01 §7.5). */
    List<ProductModel> findActiveByBrandId(Long brandId);
}
