package com.loopers.application.ranking;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

public record RankingItemInfo(
        Long rank,
        Long productId,
        String name,
        Long price,
        Long likeCount,
        BrandInfo brand,
        Double score
) {
    public static RankingItemInfo of(long rank, ProductModel product, BrandModel brand, double score) {
        return new RankingItemInfo(
                rank,
                product.getId(),
                product.getName().value(),
                product.getPrice().value(),
                product.getLikeCount(),
                BrandInfo.from(brand),
                score
        );
    }
}
