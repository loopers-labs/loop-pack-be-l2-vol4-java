package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankingMvPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RankingInfo(
    LocalDate date,
    RankingMvPeriod period,
    String periodKey,
    long totalCount,
    List<RankingProductInfo> items
) {
    public static RankingInfo ofDate(LocalDate date, long totalCount, List<RankingProductInfo> items) {
        return new RankingInfo(date, null, null, totalCount, items);
    }

    public static RankingInfo ofPeriod(
        RankingMvPeriod period, String periodKey, long totalCount, List<RankingProductInfo> items
    ) {
        return new RankingInfo(null, period, periodKey, totalCount, items);
    }

    public record RankingProductInfo(
        long rank,
        Long productId,
        String name,
        BigDecimal price,
        long likeCount,
        double score
    ) {
        public static RankingProductInfo of(long rank, Product product, double score) {
            return new RankingProductInfo(
                rank, product.getId(), product.getName(), product.getPrice(), product.getLikeCount(), score
            );
        }
    }
}
