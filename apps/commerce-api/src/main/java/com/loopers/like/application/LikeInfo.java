package com.loopers.like.application;

import com.loopers.like.domain.LikeModel;

public record LikeInfo(Long id, Long userId, Long productId) {

    public static LikeInfo from(LikeModel model) {
        return new LikeInfo(model.getId(), model.getUserId(), model.getProductId());
    }
}
