package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {

    // 동시성 안전: INSERT IGNORE 로 처리하여 중복 좋아요 등록 시 예외 없이 false 를 반환한다. true 면 삽입 성공, false 면 이미 존재.
    boolean save(LikeModel like);

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    List<LikeModel> findAllByUserId(Long userId);

    // 동시성 안전: 원자적 DELETE로 처리하여 race condition 없이 좋아요 취소의 정합성을 보장한다.
    boolean deleteByUserIdAndProductId(Long userId, Long productId);
}
