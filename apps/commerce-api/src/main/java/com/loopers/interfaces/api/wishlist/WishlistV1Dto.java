package com.loopers.interfaces.api.wishlist;

import com.loopers.application.product.ProductInfo;

public class WishlistV1Dto {

    public record LikedProductResponse(
            Long id,
            String name,
            String status,
            Long brandId
    ) {
        public static LikedProductResponse from(ProductInfo info) {
            return new LikedProductResponse(info.id(), info.name(), info.status(), info.brandId());
        }
    }
}
