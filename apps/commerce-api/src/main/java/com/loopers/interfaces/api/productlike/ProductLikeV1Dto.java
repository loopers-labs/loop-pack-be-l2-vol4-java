package com.loopers.interfaces.api.productlike;

import com.loopers.application.productlike.LikedProductInfo;

import java.util.List;

public class ProductLikeV1Dto {

    public record LikedProductResponse(
        Long productId,
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        Long likeCount
    ) {
        public static LikedProductResponse from(LikedProductInfo info) {
            return new LikedProductResponse(
                info.productId(),
                info.brandId(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.likeCount()
            );
        }
    }

    public record LikedProductsResponse(List<LikedProductResponse> products) {
        public static LikedProductsResponse from(List<LikedProductInfo> infos) {
            return new LikedProductsResponse(
                infos.stream().map(LikedProductResponse::from).toList()
            );
        }
    }
}
