package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingWeekTest {

    @DisplayName("주 중간(화요일)의 하루는 그 주의 월요일~일요일 경계와 ISO 주 식별자로 매핑된다.")
    @Test
    void mapsMidWeekDay_toMondayToSundayBounds() {
        // given : 2026-07-21 은 화요일
        LocalDate tuesday = LocalDate.of(2026, 7, 21);

        // when
        RankingWeek week = RankingWeek.from(tuesday);

        // then
        assertAll(
            () -> assertThat(week.yearWeek()).isEqualTo("2026-W30"),
            () -> assertThat(week.startDate()).isEqualTo(LocalDate.of(2026, 7, 20)),
            () -> assertThat(week.endDate()).isEqualTo(LocalDate.of(2026, 7, 26))
        );
    }

    @DisplayName("연초 목요일(2026-01-01)은 2026-W01 이며 그 주의 월요일은 전년도(2025-12-29)로 넘어간다.")
    @Test
    void spansYearBoundary_whenWeekStartsInPreviousYear() {
        // given : 2026-01-01 은 목요일, ISO 상 2026-W01
        LocalDate newYearThursday = LocalDate.of(2026, 1, 1);

        // when
        RankingWeek week = RankingWeek.from(newYearThursday);

        // then
        assertAll(
            () -> assertThat(week.yearWeek()).isEqualTo("2026-W01"),
            () -> assertThat(week.startDate()).isEqualTo(LocalDate.of(2025, 12, 29)),
            () -> assertThat(week.endDate()).isEqualTo(LocalDate.of(2026, 1, 4))
        );
    }
}
