package com.loopers.application.product;

import java.util.Optional;

public interface ProductCacheRepository {
    Optional<ProductInfo> find(Long productId);
    void save(Long productId, ProductInfo productInfo);
    void evict(Long productId);
}
