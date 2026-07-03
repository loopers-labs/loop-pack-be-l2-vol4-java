package com.loopers.domain.productlike;

import java.util.List;

public interface ProductLikeRepository {
    /**
     * (userId, productId) 좋아요를 멱등하게 insert한다. 이미 존재하면 무시하고 0을 반환한다.
     * unique 제약 위반 예외 없이 영향받은 행 수만 돌려주므로, 트랜잭션 rollback-only 오염이 발생하지 않는다.
     *
     * @return 새로 insert되면 1, 이미 좋아요 상태면 0
     */
    int insertIgnore(Long userId, Long productId);

    int deleteByUserIdAndProductId(Long userId, Long productId);

    List<Long> findLikedProductIds(Long userId);
}
