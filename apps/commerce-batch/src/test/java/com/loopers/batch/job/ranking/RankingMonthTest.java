package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingMonthTest {

    @DisplayName("달 중간의 하루는 그 달의 1일~말일 경계와 yyyy-MM 식별자로 매핑된다.")
    @Test
    void mapsMidMonthDay_toFirstToLastDayBounds() {
        // given : 2026-07-21
        LocalDate midMonth = LocalDate.of(2026, 7, 21);

        // when
        RankingMonth month = RankingMonth.from(midMonth);

        // then
        assertAll(
            () -> assertThat(month.yearMonth()).isEqualTo("2026-07"),
            () -> assertThat(month.startDate()).isEqualTo(LocalDate.of(2026, 7, 1)),
            () -> assertThat(month.endDate()).isEqualTo(LocalDate.of(2026, 7, 31))
        );
    }

    @DisplayName("2월 하루는 그 해 2월의 실제 말일(2026-02-28)까지를 경계로 갖는다.")
    @Test
    void resolvesActualLastDay_forFebruary() {
        // given : 2026-02-10 (2026 은 평년)
        LocalDate february = LocalDate.of(2026, 2, 10);

        // when
        RankingMonth month = RankingMonth.from(february);

        // then
        assertAll(
            () -> assertThat(month.yearMonth()).isEqualTo("2026-02"),
            () -> assertThat(month.startDate()).isEqualTo(LocalDate.of(2026, 2, 1)),
            () -> assertThat(month.endDate()).isEqualTo(LocalDate.of(2026, 2, 28))
        );
    }
}
