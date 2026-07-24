package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankedProductInfo;
import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.domain.ranking.RankingPeriod;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 랭킹 API 요청/응답 DTO. 응용 DTO({@link RankingPageInfo}/{@link RankedProductInfo})와 분리해 둔다.
 */
public class RankingV1Dto {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 랭킹 페이지 응답 — 기간 정보/페이지 메타 + 항목 목록.
     *
     * <p><b>{@code date} 는 하위 호환용</b>이다. 기존 일간 전용 응답의 필드를 그대로 남기고
     * 기간 시작일을 채운다 — 일간은 시작일=조회일이라 기존 클라이언트가 보던 값과 동일하다.
     * 주간/월간은 {@code periodStart}/{@code periodEnd} 로 구간을 명시한다.
     */
    public record RankingPageResponse(
            String period,
            String periodStart,
            String periodEnd,
            String date,
            int page,
            int size,
            long totalCount,
            List<RankedItem> items
    ) {
        public static RankingPageResponse from(RankingPeriod period, LocalDate baseDate, RankingPageInfo info) {
            LocalDate start = period.resolveStart(baseDate);
            LocalDate end = period.resolveEnd(start);
            return new RankingPageResponse(
                    period.name(),
                    start.format(YYYYMMDD),
                    end.format(YYYYMMDD),
                    start.format(YYYYMMDD),
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
