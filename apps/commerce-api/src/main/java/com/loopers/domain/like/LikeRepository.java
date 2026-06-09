package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface LikeRepository {

    LikeModel save(LikeModel like);

    Optional<LikeModel> findByUserIdAndProductId(UUID userId, UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    /** 유저의 좋아요 목록 — 페이징 */
    Page<LikeModel> findAllByUserId(UUID userId, Pageable pageable);

    /** 유저의 좋아요 목록 — Product+Brand fetch join, N+1 방지 */
    Page<LikeModel> findAllByUserIdWithProduct(UUID userId, Pageable pageable);

    /** 멱등 삽입 — 새로 삽입 1, 중복 0 (유니크 충돌 무시, 예외 없음) */
    int insertIgnore(UUID userId, UUID productId);

    /** 멱등 삭제 — 실제 삭제 1, 없음 0 */
    int deleteIfExists(UUID userId, UUID productId);
}
