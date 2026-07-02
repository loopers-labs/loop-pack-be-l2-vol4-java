package com.loopers.like.domain;

import java.util.List;

public interface LikeRepository {
    Like save(Like like);

    boolean exists(Long memberId, Long productId);

    void delete(Long memberId, Long productId);

    long countByProductId(Long productId);

    List<Like> findByMemberId(Long memberId);
}
