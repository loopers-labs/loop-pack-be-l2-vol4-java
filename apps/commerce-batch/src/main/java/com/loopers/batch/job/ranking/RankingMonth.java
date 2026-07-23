package com.loopers.batch.job.ranking;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 어떤 하루(requestDate)가 속한 달력 월(月)의 경계·식별자를 나타내는 값 객체(VO).
 * <p>
 * D4 결정: 월간 = 달력 월. 식별자는 {@code yyyy-MM}, 경계는 그 달의 1일 ~ 말일.
 * 주간(ISO)과 달리 연 경계 애매함이 없어 YearMonth 로 곧장 계산한다.
 */
public class RankingMonth {

    private final String yearMonth;
    private final LocalDate startDate;
    private final LocalDate endDate;

    private RankingMonth(String yearMonth, LocalDate startDate, LocalDate endDate) {
        this.yearMonth = yearMonth;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static RankingMonth from(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        return new RankingMonth(month.toString(), month.atDay(1), month.atEndOfMonth());
    }

    public String yearMonth() {
        return yearMonth;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }
}
