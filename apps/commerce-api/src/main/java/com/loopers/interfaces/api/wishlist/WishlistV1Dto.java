package com.loopers.interfaces.api.wishlist;

import com.loopers.application.wishlist.WishlistInfo;

import java.util.List;

public class WishlistV1Dto {

    public record LikedProductResponse(
            Long id,
            String name,
            String status,
            String brandName,
            Long price,
            Integer stockQuantity
    ) {
        public static LikedProductResponse from(WishlistInfo info) {
            return new LikedProductResponse(
                    info.id(),
                    info.name(),
                    info.status(),
                    info.brandName(),
                    info.price(),
                    info.stockQuantity()
            );
        }

        public static List<LikedProductResponse> from(List<WishlistInfo> infos) {
            return infos.stream().map(LikedProductResponse::from).toList();
        }
    }
}
