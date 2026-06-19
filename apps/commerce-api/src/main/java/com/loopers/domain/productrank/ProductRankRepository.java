package com.loopers.domain.productrank;

import java.util.List;

public interface ProductRankRepository {

    /**
     * likes_desc 키셋 조회. ordered product_id 를 반환한다.
     *
     * @param brandId        null 이면 무필터(전체)
     * @param lastLikeCount  직전 페이지 마지막 행의 like_count. null 이면 첫 페이지
     * @param lastProductId  직전 페이지 마지막 행의 product_id. null 이면 첫 페이지
     * @param limit          가져올 개수
     */
    List<Long> findIdsByBrandLikesDesc(Long brandId, Long lastLikeCount, Long lastProductId, int limit);

    /** 전체 재적재(재집계). */
    void replaceAll(List<ProductRank> ranks);
}
