package com.loopers.like.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface LikeRepository {

    Like save(Like like);

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    Map<Long, Long> countByProductIds(Collection<Long> productIds);

    void delete(Like like);
}
