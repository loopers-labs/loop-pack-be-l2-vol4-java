package com.loopers.domain.wishlist;

import com.loopers.domain.product.enums.ProductStatus;

public record WishlistProductSnapshot(
        Long productId,
        String productName,
        ProductStatus productStatus,
        String brandName,
        Long price,
        Integer stockQuantity
) {}
