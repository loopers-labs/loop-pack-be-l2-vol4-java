package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.ranking.RankingItemInfo;

import java.math.BigDecimal;

public class RankingV1Dto {

    public record RankingItemResponse(
        Long rank,
        Double score,
        Long productId,
        String brandName,
        String name,
        BigDecimal price,
        Long likeCount,
        boolean inStock
    ) {
        public static RankingItemResponse from(RankingItemInfo info) {
            ProductInfo product = info.product();
            return new RankingItemResponse(
                info.rank(),
                info.score(),
                product.id(),
                product.brandName(),
                product.name(),
                product.price(),
                product.likeCount(),
                product.inStock()
            );
        }
    }
}
