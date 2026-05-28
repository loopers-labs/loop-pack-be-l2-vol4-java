package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface LikeRepository {

    LikeModel save(LikeModel like);

    LikeModel saveAndFlush(LikeModel like);

    Optional<LikeModel> findByUserIdAndProductId(UUID userId, UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    /** 유저의 좋아요 목록 — 페이징 */
    Page<LikeModel> findAllByUserId(UUID userId, Pageable pageable);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);
}
