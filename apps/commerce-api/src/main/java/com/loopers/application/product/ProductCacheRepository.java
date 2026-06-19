package com.loopers.application.product;

import java.util.List;
import java.util.Optional;

public interface ProductCacheRepository {
    Optional<ProductInfo> getProduct(Long productId);

    void cacheProduct(ProductInfo productInfo);

    Optional<List<ProductInfo>> getProducts(Long brandId, String sort, Integer page, Integer size);

    void cacheProducts(Long brandId, String sort, Integer page, Integer size, List<ProductInfo> productInfos);

    void evictProduct(Long productId);

    void evictProductLists();
}
