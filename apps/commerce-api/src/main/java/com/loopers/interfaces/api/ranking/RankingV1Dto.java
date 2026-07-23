package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankedProductInfo;
import com.loopers.application.ranking.RankingPageInfo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 랭킹 API 요청/응답 DTO. 응용 DTO({@link RankingPageInfo}/{@link RankedProductInfo})와 분리해 둔다.
 */
public class RankingV1Dto {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    /** 랭킹 페이지 응답 — 조회 일자/페이지 메타 + 항목 목록. */
    public record RankingPageResponse(
            String date,
            int page,
            int size,
            long totalCount,
            List<RankedItem> items
    ) {
        public static RankingPageResponse from(LocalDate date, RankingPageInfo info) {
            return new RankingPageResponse(
                    date.format(YYYYMMDD),
                    info.page(),
                    info.size(),
                    info.totalCount(),
                    info.items().stream().map(RankedItem::from).toList()
            );
        }
    }

    /** 랭킹 항목 — 순위 + 상품 요약 + 스코어. */
    public record RankedItem(
            long rank,
            Long productId,
            String name,
            Long price,
            Long brandId,
            String brandName,
            Long likesCount,
            double score
    ) {
        public static RankedItem from(RankedProductInfo info) {
            return new RankedItem(
                    info.rank(),
                    info.productId(),
                    info.name(),
                    info.price(),
                    info.brandId(),
                    info.brandName(),
                    info.likesCount(),
                    info.score()
            );
        }
    }
}
