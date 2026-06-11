package com.loopers.domain.like;

import java.util.Optional;

public interface LikeRepository {

    Optional<LikeModel> find(LikeId id);

    LikeModel save(LikeModel like);
}