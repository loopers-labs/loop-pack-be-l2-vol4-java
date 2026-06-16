package com.loopers.application.product;

import com.loopers.support.cache.CacheStore;
import com.loopers.domain.product.SortOption;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductCompositionReader reader;
    private final CacheStore cacheStore;

    public ProductInfo getProductDetail(Long productId) {
        return cacheStore.getOrLoad(
            ProductCacheKeys.detail(productId), ProductInfo.class, ProductCacheKeys.DETAIL_TTL,
            () -> {
                ProductWithDeps c = reader.getDetail(productId);
                return ProductInfo.from(c.product(), c.brand(), c.stockQuantity() > 0);
            });
    }

    public Page<ProductInfo> search(Long brandId, SortOption sort, Pageable pageable) {
        ProductListPage page = cacheStore.getOrLoad(
            ProductCacheKeys.list(brandId, sort, pageable), ProductListPage.class, ProductCacheKeys.LIST_TTL,
            () -> {
                Page<ProductInfo> result = reader.search(brandId, sort, pageable)
                    .map(c -> ProductInfo.from(c.product(), c.brand(), c.stockQuantity() > 0));
                return new ProductListPage(result.getContent(), result.getTotalElements());
            });
        return new PageImpl<>(page.content(), pageable, page.totalElements());
    }
}
