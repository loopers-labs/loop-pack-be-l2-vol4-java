package com.loopers.application.product;

import com.loopers.domain.product.ProductPage;
import java.util.List;

public record ProductPageInfo(
    List<ProductInfo> items,
    int page,
    int size,
    long totalCount,
    int totalPages
) {
    public static ProductPageInfo from(ProductPage productPage) {
        return new ProductPageInfo(
            productPage.items().stream().map(ProductInfo::from).toList(),
            productPage.page(),
            productPage.size(),
            productPage.totalCount(),
            productPage.totalPages()
        );
    }
}
