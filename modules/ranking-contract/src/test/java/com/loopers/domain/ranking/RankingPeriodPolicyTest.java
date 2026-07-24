package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingPeriodPolicyTest {

    private final RankingPeriodPolicy rankingPeriodPolicy = new RankingPeriodPolicy();

    @Test
    @DisplayName("DAILY는 시작일과 종료일이 같아야 한다.")
    void validate_WhenDaily_ShouldRequireSameStartAndEndDate() {
        RankingDateRange dateRange = rankingPeriodPolicy.validate(
            RankingPeriod.DAILY,
            "20260714",
            "20260714"
        );

        assertThat(dateRange.startDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(dateRange.endDate()).isEqualTo(LocalDate.of(2026, 7, 14));

        assertThatThrownBy(() -> rankingPeriodPolicy.validate(
            RankingPeriod.DAILY,
            "20260714",
            "20260715"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("WEEKLY는 월요일부터 일요일까지의 7일 기간이어야 한다.")
    void validate_WhenWeekly_ShouldRequireMondayToSunday() {
        RankingDateRange dateRange = rankingPeriodPolicy.validate(
            RankingPeriod.WEEKLY,
            "20260720",
            "20260726"
        );

        assertThat(dateRange.startDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(dateRange.endDate()).isEqualTo(LocalDate.of(2026, 7, 26));

        assertThatThrownBy(() -> rankingPeriodPolicy.validate(
            RankingPeriod.WEEKLY,
            "20260721",
            "20260727"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MONTHLY는 해당 월의 1일부터 말일까지의 기간이어야 한다.")
    void validate_WhenMonthly_ShouldRequireFirstDayToLastDayOfMonth() {
        RankingDateRange dateRange = rankingPeriodPolicy.validate(
            RankingPeriod.MONTHLY,
            "20260701",
            "20260731"
        );

        assertThat(dateRange.startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(dateRange.endDate()).isEqualTo(LocalDate.of(2026, 7, 31));

        assertThatThrownBy(() -> rankingPeriodPolicy.validate(
            RankingPeriod.MONTHLY,
            "20260702",
            "20260731"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
