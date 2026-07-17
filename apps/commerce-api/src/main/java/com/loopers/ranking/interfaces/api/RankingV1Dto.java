package com.loopers.ranking.interfaces.api;

import com.loopers.product.interfaces.api.ProductDetailResponse;
import com.loopers.ranking.application.RankingItemInfo;
import com.loopers.ranking.application.RankingPageInfo;
import java.util.List;

public class RankingV1Dto {

    public record RankingItemResponse(
            long rank, double score, ProductDetailResponse product) {
        public static RankingItemResponse from(RankingItemInfo info) {
            return new RankingItemResponse(
                    info.rank(), info.score(), ProductDetailResponse.from(info.product()));
        }
    }

    public record RankingPageResponse(
            List<RankingItemResponse> items,
            int page,
            int size,
            long totalCount,
            int totalPages) {
        public static RankingPageResponse from(RankingPageInfo info) {
            return new RankingPageResponse(
                    info.items().stream().map(RankingItemResponse::from).toList(),
                    info.page(),
                    info.size(),
                    info.totalCount(),
                    info.totalPages());
        }
    }
}
