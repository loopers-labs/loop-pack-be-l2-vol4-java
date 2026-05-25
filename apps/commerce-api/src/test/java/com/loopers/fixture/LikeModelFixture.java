package com.loopers.fixture;

import com.loopers.domain.like.LikeModel;

public class LikeModelFixture {

    private Long userId = 1L;
    private Long productId = 1L;

    public static LikeModelFixture aLike() {
        return new LikeModelFixture();
    }

    public LikeModelFixture withUserId(Long userId) { this.userId = userId; return this; }
    public LikeModelFixture withProductId(Long productId) { this.productId = productId; return this; }

    public LikeModel build() {
        return new LikeModel(userId, productId);
    }
}
