package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    Optional<ProductModel> findActive(Long id);
    List<ProductModel> findAll(ProductSortType sort, int page, int size);
    List<ProductModel> findAllActiveByBrandId(Long brandId);
    void delete(Long id);

    /**
     * active 상품의 like_count 를 1 증가시킨다.
     * @return 영향 행 수 (0 또는 1)
     */
    int incrementLikeCount(Long id);

    /**
     * active 상품의 like_count 를 1 감소시킨다.
     * @return 영향 행 수 (0 또는 1)
     */
    int decrementLikeCount(Long id);
}
