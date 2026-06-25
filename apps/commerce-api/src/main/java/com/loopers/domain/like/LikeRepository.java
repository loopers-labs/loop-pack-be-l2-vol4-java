package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    Optional<LikeModel> findByMemberIdAndProductId(Long memberId, Long productId);
    List<LikeModel> findAllByMemberId(Long memberId);
    List<LikeModel> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
    void delete(LikeModel like);
    void deleteAllByProductId(Long productId);
}
