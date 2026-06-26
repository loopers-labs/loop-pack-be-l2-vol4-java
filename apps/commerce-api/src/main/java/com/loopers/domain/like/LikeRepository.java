package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    LikeEntity save(LikeEntity like);
    Optional<LikeEntity> findActive(String userId, String productId);
    Optional<LikeEntity> findAny(String userId, String productId);
    Page<LikeEntity> findActiveByUserId(String userId, Pageable pageable);
    void deleteAllByProductId(String productId);
    void deleteAllByProductIds(List<String> productIds);
}
