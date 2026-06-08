package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

public class LikeV1Dto {

    public record LikeResponse(
        Long productId,
        String productName,
        Long price,
        String brandName
    ) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(
                info.productId(),
                info.productName(),
                info.price(),
                info.brandName()
            );
        }
    }
}
