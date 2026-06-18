package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findByUserId(Long userId);
    LikeModel save(LikeModel like);

    /** flush를 즉시 강제하여 UK 위반을 try-catch 경계 안에서 잡기 위해 사용한다. */
    LikeModel saveAndFlush(LikeModel like);

    /** @return 실제 삭제된 행 수(0 또는 1). like_count 동기화 여부 판단에 사용. */
    int delete(Long userId, Long productId);

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
}
