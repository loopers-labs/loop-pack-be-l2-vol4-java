package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 배치 파라미터(baseDate, period)를 "무엇을 어디까지 읽어 어떤 키로 적재할지"로 해석한 결과.
 * Reader/Processor/Tasklet 이 각자 파라미터를 파싱하면 해석이 어긋날 수 있어 한 곳으로 모았다.
 */
public record AggregationTarget(RankingPeriod period, String periodKey, LocalDate from, LocalDate to) {

    private static final DateTimeFormatter BASIC_ISO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public static AggregationTarget of(String baseDate, String period) {
        RankingPeriod resolved = RankingPeriod.from(period);
        if (resolved == RankingPeriod.DAILY) {
            throw new IllegalArgumentException(
                "일간 랭킹은 MV 적재 대상이 아닙니다(Redis 실시간 랭킹이 담당). period 는 WEEKLY|MONTHLY 입니다.");
        }
        LocalDate base = parse(baseDate);
        return new AggregationTarget(resolved, resolved.periodKey(base), resolved.startOf(base), resolved.endOf(base));
    }

    private static LocalDate parse(String baseDate) {
        if (baseDate == null || baseDate.isBlank()) {
            throw new IllegalArgumentException("baseDate 는 필수입니다. (yyyyMMdd)");
        }
        try {
            return LocalDate.parse(baseDate.trim(), BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("baseDate 형식은 yyyyMMdd 입니다: " + baseDate);
        }
    }
}
