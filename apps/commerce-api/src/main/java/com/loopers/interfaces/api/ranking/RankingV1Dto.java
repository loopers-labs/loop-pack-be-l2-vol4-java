package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public class RankingV1Dto {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 요청 파라미터 date 를 yyyyMMdd 형식으로만 엄격히 파싱한다. 형식 위반은 인터페이스 경계에서 400.
     */
    public static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date 는 yyyyMMdd 형식이어야 합니다: " + date);
        }
    }

    /**
     * 요청 파라미터 period 를 랭킹 기간으로 파싱한다. 생략(null/blank) 시 일간(DAILY) 기본값(하위 호환).
     * 정의되지 않은 값은 인터페이스 경계에서 400.
     */
    public static RankingPeriod parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return RankingPeriod.DAILY;
        }
        try {
            return RankingPeriod.valueOf(period.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period 는 daily|weekly|monthly 중 하나여야 합니다: " + period);
        }
    }

    public record RankingPageResponse(int page, int size, List<RankingResponse> rankings) {
        public static RankingPageResponse of(int page, int size, List<RankingInfo> infos) {
            List<RankingResponse> rankings = infos.stream()
                .map(RankingResponse::from)
                .toList();
            return new RankingPageResponse(page, size, rankings);
        }
    }

    public record RankingResponse(long rank, RankedProductResponse product, double score) {
        public static RankingResponse from(RankingInfo info) {
            return new RankingResponse(info.rank(), RankedProductResponse.from(info.product()), info.score());
        }
    }

    public record RankedProductResponse(Long id, String name, Long price, long likeCount, Long brandId) {
        public static RankedProductResponse from(RankingInfo.RankedProduct product) {
            return new RankedProductResponse(
                product.id(),
                product.name(),
                product.price(),
                product.likeCount(),
                product.brandId()
            );
        }
    }
}
