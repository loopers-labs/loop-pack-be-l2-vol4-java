package com.loopers.interfaces.api.catalog.ranking;

import com.loopers.application.catalog.ranking.RankingResult;
import com.loopers.interfaces.api.catalog.product.ProductV1Dto;

public class RankingDto {

    public record RankingListItemResponse(
        Long rank,
        Double score,
        ProductV1Dto.ProductListItemResponse product
    ) {
        public static RankingListItemResponse from(RankingResult result) {
            return new RankingListItemResponse(
                result.rank(),
                result.score(),
                ProductV1Dto.ProductListItemResponse.from(result.product())
            );
        }
    }
}
