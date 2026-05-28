package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;

import java.util.List;

public interface LikeRepository {

    /**
     * (userId, productId) Like 가 존재하지 않을 때만 새로 저장한다.
     *
     * @return 신규로 저장되었으면 true, 이미 존재해서 저장하지 않았으면 false
     */
    boolean saveIfAbsent(LikeModel like);

    /**
     * (userId, productId) Like 가 존재하면 삭제한다.
     *
     * @return 삭제된 행 수 (0 또는 1)
     */
    int deleteByUserIdAndProductId(Long userId, Long productId);

    /**
     * userId 가 좋아요한 active 상품을 좋아요 누른 시각 최신순으로 페이지 단위 반환한다.
     * soft-deleted 된 상품은 결과에서 제외된다.
     */
    List<ProductModel> findLikedActiveProductsByUserId(Long userId, int page, int size);
}
