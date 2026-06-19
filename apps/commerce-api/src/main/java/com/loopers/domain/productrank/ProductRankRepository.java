package com.loopers.domain.productrank;

import java.util.List;

public interface ProductRankRepository {

    /**
     * likes_desc 키셋 조회. (product_id, like_count) 를 정렬 순서대로 반환한다.
     * 커서·표시 모두 여기서 나온 rank 의 like_count 를 사용해 정렬과 일관시킨다.
     *
     * @param brandId        null 이면 무필터(전체)
     * @param lastLikeCount  직전 페이지 마지막 행의 like_count. null 이면 첫 페이지
     * @param lastProductId  직전 페이지 마지막 행의 product_id. null 이면 첫 페이지
     * @param limit          가져올 개수
     */
    List<RankedProduct> findRankedByBrandLikesDesc(Long brandId, Long lastLikeCount, Long lastProductId, int limit);

    /** 전체 재적재. 테스트/명시 적재용. */
    void replaceAll(List<ProductRank> ranks);

    /**
     * source(product + product_like_count)에서 product_rank 전체를 재적재 = 재집계.
     * like_count 는 source 절대값으로 맞춰 드리프트를 보정하고, 삭제된 상품은 제외한다.
     */
    void rebuildFromSource();
}
