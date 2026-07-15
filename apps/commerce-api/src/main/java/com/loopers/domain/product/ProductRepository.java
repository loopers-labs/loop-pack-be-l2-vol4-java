package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    List<ProductModel> findAll();
    void delete(Long id);

    ProductPage search(ProductSearchCondition condition);

    /** like_count 를 원자적으로 1 증가시킨다. */
    int incrementLikeCount(Long id);

    /** like_count 를 원자적으로 1 감소시킨다. 0 미만으로 내려가지 않는다. */
    int decrementLikeCount(Long id);
}
