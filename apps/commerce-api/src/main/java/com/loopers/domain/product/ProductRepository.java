package com.loopers.domain.product;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);

    /** 특정 브랜드의 활성 상품 전체 — Brand 비활성 시 cascade 전파용 (01 §7.5). */
    List<ProductModel> findActiveByBrandId(Long brandId);

    /** 주어진 id 들 중 활성 상품만 batch 조회 — 목록/좋아요한 상품 조합 N+1 회피 (UC-03/07). */
    List<ProductModel> findActiveByIds(Collection<Long> ids);
}
