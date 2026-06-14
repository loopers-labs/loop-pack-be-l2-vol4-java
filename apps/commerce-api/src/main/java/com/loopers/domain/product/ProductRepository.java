package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(Long id);
    List<Product> findAllByIdForUpdate(List<Long> ids);
    List<Product> findAll(Long brandId, ProductSort sort, int page, int size);
    long count(Long brandId);
    void increaseLikeCount(Long id);
    void decreaseLikeCount(Long id);
}
