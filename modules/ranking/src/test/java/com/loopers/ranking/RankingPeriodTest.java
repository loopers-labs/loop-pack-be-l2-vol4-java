package com.loopers.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingPeriodTest {

    @DisplayName("주간 기간은 requestDate가 속한 월요일부터 일요일까지다.")
    @Test
    void resolvesWeeklyRange() {
        var range = RankingPeriod.WEEKLY.rangeOf(LocalDate.of(2026, 1, 1));

        assertThat(range.start()).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 1, 4));
    }

    @DisplayName("월간 기간은 requestDate가 속한 달의 1일부터 말일까지다.")
    @Test
    void resolvesMonthlyRange() {
        var range = RankingPeriod.MONTHLY.rangeOf(LocalDate.of(2024, 2, 10));

        assertThat(range.start()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @DisplayName("종료일이 오늘보다 이전인 기간만 완료된 기간이다.")
    @Test
    void determinesCompletedPeriod() {
        LocalDate today = LocalDate.of(2026, 1, 5);

        assertThat(RankingPeriod.WEEKLY.isCompleted(LocalDate.of(2026, 1, 1), today)).isTrue();
        assertThat(RankingPeriod.WEEKLY.isCompleted(today, today)).isFalse();
        assertThat(RankingPeriod.MONTHLY.isCompleted(LocalDate.of(2025, 12, 15), today)).isTrue();
        assertThat(RankingPeriod.MONTHLY.isCompleted(today, today)).isFalse();
    }
}
