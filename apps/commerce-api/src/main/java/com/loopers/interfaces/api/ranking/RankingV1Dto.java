package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class RankingV1Dto {

    private RankingV1Dto() {
    }

    /** date 파라미터(yyyyMMdd, 생략 시 오늘 KST) 해석. */
    public static LocalDate parseDateOrToday(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(RankingKeys.ZONE);
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date 는 yyyyMMdd 형식이어야 합니다.");
        }
    }

    public record RankedItemResponse(
            long rank,
            Long productId,
            Long brandId,
            String brandName,
            String name,
            long price,
            long likeCount,
            double score
    ) {

        public static RankedItemResponse from(RankingInfo.RankedItem info) {
            return new RankedItemResponse(
                    info.rank(),
                    info.productId(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.likeCount(),
                    info.score()
            );
        }
    }

    public record PageResponse(
            String date,
            List<RankedItemResponse> content,
            int page,
            int size,
            boolean hasNext,
            long totalElements
    ) {

        public static PageResponse from(LocalDate date, PageResult<RankingInfo.RankedItem> result) {
            return new PageResponse(
                    date.format(DateTimeFormatter.BASIC_ISO_DATE),
                    result.content().stream().map(RankedItemResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.hasNext(),
                    result.totalElements()
            );
        }
    }
}
