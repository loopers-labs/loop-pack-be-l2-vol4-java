package com.loopers.batch.job.catalog.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record ProductRankingJobParameters(
    ProductRankingPeriod period,
    LocalDate baseDate,
    ProductRankingPeriodRange range
) {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    public static ProductRankingJobParameters of(String period, String baseDate) {
        ProductRankingPeriod rankingPeriod = ProductRankingPeriod.from(period);
        LocalDate parsedBaseDate = parseBaseDate(baseDate);
        return new ProductRankingJobParameters(
            rankingPeriod,
            parsedBaseDate,
            ProductRankingPeriodRange.of(rankingPeriod, parsedBaseDate)
        );
    }

    private static LocalDate parseBaseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("baseDate JobParameter는 필수입니다.");
        }

        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("baseDate JobParameter는 yyyyMMdd 형식이어야 합니다.", e);
        }
    }
}
