package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikedProductInfo;

public class LikeV1Dto {

    public record LikedProductResponse(
        Long id,
        String name,
        String description,
        Long price,
        long likeCount,
        BrandResponse brand
    ) {
        public static LikedProductResponse from(LikedProductInfo info) {
            return new LikedProductResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.likeCount(),
                BrandResponse.from(info.brand())
            );
        }
    }

    public record BrandResponse(Long id, String name) {
        public static BrandResponse from(LikedProductInfo.BrandSummary summary) {
            return new BrandResponse(summary.id(), summary.name());
        }
    }
}
