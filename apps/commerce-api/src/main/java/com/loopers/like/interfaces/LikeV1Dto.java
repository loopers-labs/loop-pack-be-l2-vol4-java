package com.loopers.like.interfaces;

import com.loopers.like.application.LikeInfo;

public class LikeV1Dto {

    public record LikeResponse(Long id, Long userId, Long productId) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(info.id(), info.userId(), info.productId());
        }
    }
}
