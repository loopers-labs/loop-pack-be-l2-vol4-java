package com.loopers.tddstudy.domain.ranking;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class RankingPeriodTest {

    @Test
    void 월요일_기준_직전_주는_전주_월요일부터_일요일까지다() {
        // 2026-07-06(월) 기준 → 직전 주는 6/29(월)~7/5(일)
        RankingPeriod week = RankingPeriod.lastCompletedWeek(LocalDate.of(2026, 7, 6));

        assertThat(week.startDate()).isEqualTo(LocalDate.of(2026, 6, 29));
        assertThat(week.endDate()).isEqualTo(LocalDate.of(2026, 7, 5));
    }

    @Test
    void 주중_기준이어도_직전에_끝난_주를_가리킨다() {
        // 2026-07-08(수) → 이번 주(7/6~7/12)는 아직 안 끝남 → 여전히 6/29~7/5
        RankingPeriod week = RankingPeriod.lastCompletedWeek(LocalDate.of(2026, 7, 8));

        assertThat(week.startDate()).isEqualTo(LocalDate.of(2026, 6, 29));
        assertThat(week.endDate()).isEqualTo(LocalDate.of(2026, 7, 5));
    }

    @Test
    void 달을_넘어가는_주도_하나의_주로_묶인다() {
        // 6/29~7/5는 6월·7월에 걸쳐있지만 한 주
        RankingPeriod week = RankingPeriod.lastCompletedWeek(LocalDate.of(2026, 7, 6));

        assertThat(week.startDate().getMonthValue()).isEqualTo(6);
        assertThat(week.endDate().getMonthValue()).isEqualTo(7);
        assertThat(week.key()).isEqualTo("2026-W27");
    }

    @Test
    void 직전_달은_그_달의_1일부터_말일까지다() {
        // 2026-08-01 기준 → 7월 전체
        RankingPeriod month = RankingPeriod.lastCompletedMonth(LocalDate.of(2026, 8, 1));

        assertThat(month.key()).isEqualTo("2026-07");
        assertThat(month.startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(month.endDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void 달_중간_기준이어도_직전_달_전체를_가리킨다() {
        RankingPeriod month = RankingPeriod.lastCompletedMonth(LocalDate.of(2026, 8, 20));

        assertThat(month.startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(month.endDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void 일월_기준_직전_달은_작년_12월이다() {
        RankingPeriod month = RankingPeriod.lastCompletedMonth(LocalDate.of(2026, 1, 10));

        assertThat(month.key()).isEqualTo("2025-12");
        assertThat(month.endDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void 이월_말일은_그_해에_맞게_계산된다() {
        // 2026년은 평년 → 2월 28일
        RankingPeriod month = RankingPeriod.lastCompletedMonth(LocalDate.of(2026, 3, 5));

        assertThat(month.endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

}

