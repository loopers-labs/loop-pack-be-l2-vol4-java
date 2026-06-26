package com.loopers.product.application;

import com.loopers.product.domain.ProductSortOption;

public class ProductCommand {

    public record PageQuery(
        Long brandId,
        ProductSortOption sort,
        int page,
        int size
    ) {
    }

    public record Create(
        Long brandId,
        String name,
        String description,
        long price,
        String thumbnailUrl,
        int initialStockQuantity
    ) {
    }

    public record Update(
        Long productId,
        String name,
        String description,
        long price,
        String thumbnailUrl
    ) {
    }
}
