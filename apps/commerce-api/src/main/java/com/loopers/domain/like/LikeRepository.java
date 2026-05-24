package com.loopers.domain.like;

import java.util.Optional;

public interface LikeRepository {

    Like save(Like like);

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    void delete(Like like);
}
