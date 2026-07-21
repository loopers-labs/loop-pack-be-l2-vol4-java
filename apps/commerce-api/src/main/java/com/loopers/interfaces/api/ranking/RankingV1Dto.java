package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.ranking.RankingInfo;

public class RankingV1Dto {

    public record RankingResponse(
        int rank,
        Long productId,
        Long brandId,
        String name,
        Long price,
        boolean inStock,
        Integer remainingStock,
        Long likeCount
    ) {
        public static RankingResponse from(RankingInfo info) {
            ProductInfo product = info.product();
            return new RankingResponse(
                info.rank(),
                product.id(),
                product.brandId(),
                product.name(),
                product.price(),
                product.inStock(),
                product.remainingStock(),
                product.likeCount()
            );
        }
    }
}
