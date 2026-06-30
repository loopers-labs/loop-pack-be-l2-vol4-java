package com.loopers.product.application;

import com.loopers.product.application.cache.ProductCacheStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 상품 조회 앞단의 look-aside 캐시 계층. 캐시 적중 시 DB 트랜잭션을 전혀 열지 않는다.
 * 목록은 트래픽이 몰리는 첫 페이지(page=0)만 캐싱하고, 그 외 페이지는 DB 로 위임한다.
 */
@Component
@RequiredArgsConstructor
public class ProductReadCacheService {

    private final ProductCacheStore cacheStore;
    private final ProductQueryService productQueryService;

    public ProductResult.Detail getProduct(Long productId) {
        return cacheStore.getDetail(productId)
                .orElseGet(() -> {
                    ProductResult.Detail detail = productQueryService.getProduct(productId);
                    cacheStore.putDetail(productId, detail);
                    return detail;
                });
    }

    public ProductResult.Page getProducts(ProductCommand.PageQuery query) {
        if (query.page() != 0) {
            return productQueryService.getProducts(query);
        }
        return cacheStore.getFirstPage(query)
                .orElseGet(() -> {
                    ProductResult.Page page = productQueryService.getProducts(query);
                    cacheStore.putFirstPage(query, page);
                    return page;
                });
    }
}
