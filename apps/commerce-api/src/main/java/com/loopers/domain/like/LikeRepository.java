package com.loopers.domain.like;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LikeRepository {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findByUserId(Long userId);
    LikeModel save(LikeModel like);

    /** flush를 즉시 강제하여 UK 위반을 try-catch 경계 안에서 잡기 위해 사용한다. */
    LikeModel saveAndFlush(LikeModel like);
    void delete(Long userId, Long productId);
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
    long countByProductId(Long productId);

    /**
     * 여러 상품의 좋아요 수를 일괄 조회한다. (목록 조회 시 N+1 회피)
     *
     * @return {@code productId → count} 맵. 좋아요가 0개인 상품은 맵에 포함되지 않는다.
     */
    Map<Long, Long> countByProductIdIn(List<Long> productIds);
}
