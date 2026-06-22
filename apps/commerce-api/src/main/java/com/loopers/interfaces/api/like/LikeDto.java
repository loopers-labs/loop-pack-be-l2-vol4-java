package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

public class LikeDto {

    public record LikeResponse(Long likeId, Long productId, String productName, Long productPrice) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(info.likeId(), info.productId(), info.productName(), info.productPrice());
        }
    }
}
