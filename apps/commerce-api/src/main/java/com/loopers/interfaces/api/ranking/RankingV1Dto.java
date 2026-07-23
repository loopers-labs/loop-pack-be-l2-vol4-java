package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.ranking.RankingPageInfo;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class RankingV1Dto {

    public record RankingPageResponse(
        String date,
        int page,
        int size,
        long totalCount,
        List<RankingItemResponse> items
    ) {
        public static RankingPageResponse from(RankingPageInfo info) {
            return new RankingPageResponse(
                info.date().format(DateTimeFormatter.BASIC_ISO_DATE),
                info.page(),
                info.size(),
                info.totalCount(),
                info.items().stream().map(RankingItemResponse::from).toList()
            );
        }
    }

    public record RankingItemResponse(
        long rank,
        Long productId,
        String name,
        int price,
        String brandName,
        long likeCount
    ) {
        public static RankingItemResponse from(RankingPageInfo.RankedProduct item) {
            ProductInfo product = item.product();
            return new RankingItemResponse(
                item.rank(),
                product.id(),
                product.name(),
                product.price(),
                product.brandName(),
                product.likeCount()
            );
        }
    }
}
