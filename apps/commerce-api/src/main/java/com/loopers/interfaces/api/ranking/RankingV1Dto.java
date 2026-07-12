package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;
import com.loopers.application.support.PageResult;
import com.loopers.interfaces.api.product.ProductV1Dto;

import java.util.List;

public class RankingV1Dto {

    public record RankingResponse(
        long rank,
        ProductV1Dto.ProductResponse product
    ) {
        public static RankingResponse from(RankingInfo info) {
            return new RankingResponse(info.rank(), ProductV1Dto.ProductResponse.from(info.product()));
        }
    }

    public record RankingPageResponse(
        List<RankingResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static RankingPageResponse from(PageResult<RankingInfo> page) {
            return new RankingPageResponse(
                page.items().stream().map(RankingResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
            );
        }
    }
}
