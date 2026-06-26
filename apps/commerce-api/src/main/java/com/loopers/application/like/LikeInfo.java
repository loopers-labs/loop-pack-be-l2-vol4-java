package com.loopers.application.like;

import com.loopers.domain.brand.BrandEntity;
import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.product.ProductEntity;

public record LikeInfo(
    String id,
    String brandId,
    String brandName,
    String name,
    Long price,
    Long likeCount
) {
    public static LikeInfo from(ProductEntity product, BrandEntity brand) {
        return new LikeInfo(
            product.getId(),
            product.getBrandId(),
            brand.getName(),
            product.getName(),
            product.getPrice(),
            product.getLikeCount()
        );
    }
}
