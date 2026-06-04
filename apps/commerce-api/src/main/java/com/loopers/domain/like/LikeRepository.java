package com.loopers.domain.like;

import com.loopers.domain.common.PageResult;

import java.util.Collection;
import java.util.Map;

public interface LikeRepository {

    void save(Like like);

    void delete(Long userId, Long productId);

    boolean exists(Long userId, Long productId);

    long countByProductId(Long productId);

    Map<Long, Long> countByProductIds(Collection<Long> productIds);

    PageResult<Like> findAllByUserId(Long userId, int page, int size);
}
