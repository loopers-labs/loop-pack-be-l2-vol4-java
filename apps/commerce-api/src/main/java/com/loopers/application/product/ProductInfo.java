package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

/**
 * 상품 응용 DTO. Product + Brand 조합 결과.
 */
public record ProductInfo(
        Long id,
        String name,
        String description,
        long price,
        int likeCount,
        boolean isAvailable,
        Long brandId,
        String brandName,
        String imageUrl
) {
    public static ProductInfo from(ProductModel product, BrandModel brand) {
        return new ProductInfo(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().amount(),
                product.getLikeCount(),
                product.isAvailable(),
                brand.getId(),
                brand.getName(),
                product.getImageUrl()
        );
    }
}
