package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.application.ranking.RankingPageInfo;

import java.util.List;

public class RankingV1Dto {

    public record RankingItemResponse(
        long rank,
        Long productId,
        String name,
        Long price,
        long likeCount,
        double score
    ) {
        public static RankingItemResponse from(RankingItemInfo info) {
            return new RankingItemResponse(
                info.rank(), info.productId(), info.name(), info.price(), info.likeCount(), info.score());
        }
    }

    public record RankingPageResponse(
        String date,
        int page,
        int size,
        long totalCount,
        List<RankingItemResponse> items
    ) {
        public static RankingPageResponse from(RankingPageInfo info) {
            return new RankingPageResponse(
                info.date(), info.page(), info.size(), info.totalCount(),
                info.items().stream().map(RankingItemResponse::from).toList());
        }
    }
}
