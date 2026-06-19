package com.loopers.product.application;

public interface ProductDetailCacheEvictor {

    void evict(Long productId);
}
