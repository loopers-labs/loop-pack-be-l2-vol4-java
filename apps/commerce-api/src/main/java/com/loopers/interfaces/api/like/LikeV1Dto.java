package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

public class LikeV1Dto {

    public record LikeResponse(
            Long id,
            Long brandId,
            String brandName,
            String name,
            Long price,
            Long likeCount
    ) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.likeCount()
            );
        }
    }
}
