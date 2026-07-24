package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class RankingV1Dto {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public record RankingPageResponse(
        String date,
        String period,
        String periodKey,
        long totalCount,
        List<RankingItemResponse> items
    ) {
        public static RankingPageResponse from(RankingInfo info) {
            return new RankingPageResponse(
                info.date() != null ? info.date().format(DATE_FORMATTER) : null,
                info.period() != null ? info.period().name() : null,
                info.periodKey(),
                info.totalCount(),
                info.items().stream().map(RankingItemResponse::from).toList()
            );
        }
    }

    public record RankingItemResponse(
        long rank,
        Long productId,
        String name,
        BigDecimal price,
        long likeCount
    ) {
        public static RankingItemResponse from(RankingInfo.RankingProductInfo item) {
            return new RankingItemResponse(
                item.rank(), item.productId(), item.name(), item.price(), item.likeCount()
            );
        }
    }
}
