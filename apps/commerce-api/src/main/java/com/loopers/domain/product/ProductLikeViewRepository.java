package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductLikeViewRepository {
    ProductLikeViewModel save(ProductLikeViewModel view);
    Optional<ProductLikeViewModel> findByProductId(Long productId);
    Optional<ProductLikeViewModel> findByProductIdForUpdate(Long productId);
    List<ProductLikeViewModel> findAllByProductIdIn(List<Long> productIds);
    void deleteByProductId(Long productId);
}
