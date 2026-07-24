package com.loopers.domain.ranking.batch;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 랭킹 배치 Job 파라미터(period, periodKey)를 검증하고, periodKey로부터 실제 집계 대상
 * 날짜 범위([startDate, endDate])를 계산한다. WEEKLY는 ISO-8601 주(월요일 시작) 기준,
 * MONTHLY는 해당 월의 1일~말일 기준이다.
 */
public record RankingBatchJobParameters(
    RankingBatchPeriodType periodType,
    String periodKey,
    LocalDate startDate,
    LocalDate endDate
) {

    private static final Pattern WEEKLY_PERIOD_KEY = Pattern.compile("^(\\d{4})W(0[1-9]|[1-4]\\d|5[0-3])$");
    private static final Pattern MONTHLY_PERIOD_KEY = Pattern.compile("^(\\d{4})(0[1-9]|1[0-2])$");

    public static RankingBatchJobParameters of(String period, String periodKey) {
        RankingBatchPeriodType periodType = parsePeriodType(period);
        return switch (periodType) {
            case WEEKLY -> ofWeekly(periodKey);
            case MONTHLY -> ofMonthly(periodKey);
        };
    }

    private static RankingBatchPeriodType parsePeriodType(String period) {
        if (period == null) {
            throw new IllegalArgumentException("period는 필수입니다.");
        }
        try {
            return RankingBatchPeriodType.valueOf(period);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("period는 WEEKLY 또는 MONTHLY여야 합니다: " + period);
        }
    }

    private static RankingBatchJobParameters ofWeekly(String periodKey) {
        Matcher matcher = WEEKLY_PERIOD_KEY.matcher(periodKey == null ? "" : periodKey);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("WEEKLY periodKey는 yyyyWww(주차 01~53) 형식이어야 합니다: " + periodKey);
        }

        int isoWeekBasedYear = Integer.parseInt(matcher.group(1));
        int isoWeek = Integer.parseInt(matcher.group(2));

        // ISO-8601 규칙상 1월 4일은 항상 그 해의 1주차에 속하므로, 그 주의 월요일을 기준점으로 삼는다.
        LocalDate mondayOfWeek1 = LocalDate.of(isoWeekBasedYear, 1, 4).with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDate startDate = mondayOfWeek1.plusWeeks(isoWeek - 1L);
        LocalDate endDate = startDate.plusDays(6);

        return new RankingBatchJobParameters(RankingBatchPeriodType.WEEKLY, periodKey, startDate, endDate);
    }

    private static RankingBatchJobParameters ofMonthly(String periodKey) {
        Matcher matcher = MONTHLY_PERIOD_KEY.matcher(periodKey == null ? "" : periodKey);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("MONTHLY periodKey는 yyyyMM(월 01~12) 형식이어야 합니다: " + periodKey);
        }

        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        YearMonth yearMonth = YearMonth.of(year, month);

        return new RankingBatchJobParameters(
            RankingBatchPeriodType.MONTHLY, periodKey, yearMonth.atDay(1), yearMonth.atEndOfMonth()
        );
    }
}
