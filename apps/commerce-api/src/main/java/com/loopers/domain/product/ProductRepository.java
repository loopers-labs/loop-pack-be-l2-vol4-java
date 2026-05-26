package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);

    /** 특정 브랜드의 활성 상품 전체 — Brand 비활성 시 cascade 전파용 (01 §7.5). */
    List<ProductModel> findActiveByBrandId(Long brandId);

    /** 활성 상품 목록 — 브랜드 필터(null=전체) + 정렬 + 페이지 (UC-03). */
    List<ProductModel> findActivePage(Long brandId, ProductSortType sort, int page, int size);
}
