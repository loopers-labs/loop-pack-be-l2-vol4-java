package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

import java.time.ZonedDateTime;

public class LikeV1Dto {

    public record LikeResponse(
        Long productId,
        String productName,
        int price,
        String brandName,
        ZonedDateTime likedAt
    ) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(
                info.productId(),
                info.productName(),
                info.price(),
                info.brandName(),
                info.likedAt()
            );
        }
    }
}
