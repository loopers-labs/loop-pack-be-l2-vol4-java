package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.interfaces.api.brand.BrandV1Dto;

public class RankingV1Dto {

    public record RankingItemResponse(
            Long rank,
            Long productId,
            String name,
            Long price,
            Long likeCount,
            BrandV1Dto.BrandResponse brand,
            Double score
    ) {
        public static RankingItemResponse from(RankingItemInfo info) {
            return new RankingItemResponse(
                    info.rank(),
                    info.productId(),
                    info.name(),
                    info.price(),
                    info.likeCount(),
                    BrandV1Dto.BrandResponse.from(info.brand()),
                    info.score()
            );
        }
    }
}