package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

public class RankingV1Dto {

    public record RankingResponse(
        long rank,
        Long productId,
        String productName,
        String brandName,
        Long price,
        double score,
        boolean fallback
    ) {
        public static RankingResponse from(RankingInfo info) {
            return new RankingResponse(
                info.rank(),
                info.productId(),
                info.productName(),
                info.brandName(),
                info.price(),
                info.score(),
                info.fallback()
            );
        }
    }
}
