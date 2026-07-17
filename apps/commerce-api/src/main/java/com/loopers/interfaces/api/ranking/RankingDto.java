package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingItemInfo;

import java.util.List;

public class RankingDto {

    public record RankingItemResponse(int rank, Long productId, String productName, Long price, Long brandId, double score) {
        public static RankingItemResponse from(RankingItemInfo info) {
            return new RankingItemResponse(info.rank(), info.productId(), info.productName(), info.price(), info.brandId(), info.score());
        }
    }

    public record RankingPageResponse(String date, List<RankingItemResponse> rankings, long totalElements, int totalPages) {}
}
