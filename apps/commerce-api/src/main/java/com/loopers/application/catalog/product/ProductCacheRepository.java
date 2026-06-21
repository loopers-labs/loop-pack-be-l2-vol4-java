package com.loopers.application.catalog.product;

import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.support.pagination.PageResult;

import java.util.Optional;

public interface ProductCacheRepository {
    Optional<ProductResult> getDetail(Long productId);

    void putDetail(Long productId, ProductResult product);

    Optional<PageResult<ProductResult>> getList(ProductSearchCondition condition);

    void putList(ProductSearchCondition condition, PageResult<ProductResult> products);

    void evictDetail(Long productId);

    void evictLists();
}
