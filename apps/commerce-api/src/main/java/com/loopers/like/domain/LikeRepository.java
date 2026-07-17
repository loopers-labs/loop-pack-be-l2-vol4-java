package com.loopers.like.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface LikeRepository {
    boolean saveIfAbsent(Long memberId, Long productId);

    void delete(Long memberId, Long productId);

    long countByProductId(Long productId);

    Map<Long, Long> countByProductIds(Collection<Long> productIds);

    List<Like> findByMemberId(Long memberId);
}
