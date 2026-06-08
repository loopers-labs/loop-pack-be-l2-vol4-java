package com.loopers.application.wishlist;

import com.loopers.domain.wishlist.WishlistProductSnapshot;

public record WishlistInfo(
        Long id,
        String name,
        String status,
        String brandName,
        Long price,
        Integer stockQuantity
) {
    public static WishlistInfo from(WishlistProductSnapshot snapshot) {
        return new WishlistInfo(
                snapshot.productId(),
                snapshot.productName(),
                snapshot.productStatus().getDescription(),
                snapshot.brandName(),
                snapshot.price(),
                snapshot.stockQuantity()
        );
    }
}
