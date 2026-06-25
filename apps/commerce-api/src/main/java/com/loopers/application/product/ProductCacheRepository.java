package com.loopers.application.product;

import java.util.Optional;

public interface ProductCacheRepository {
    Optional<ProductDetailCache> find(Long productId);
    void save(Long productId, ProductDetailCache productDetail);
    void evict(Long productId);
}
