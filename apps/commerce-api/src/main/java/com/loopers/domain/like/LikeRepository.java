package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    LikeEntity save(LikeEntity like);
    Optional<LikeEntity> findActive(Long userId, Long productId);
    Optional<LikeEntity> findAny(Long userId, Long productId);
    Page<LikeEntity> findActiveByUserId(Long userId, Pageable pageable);
    void deleteAllByProductId(Long productId);
    void deleteAllByProductIds(List<Long> productIds);
}
