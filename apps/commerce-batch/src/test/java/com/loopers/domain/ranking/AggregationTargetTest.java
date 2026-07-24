package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregationTargetTest {

    @DisplayName("baseDate(yyyyMMdd) + period 로 적재 키와 집계 구간이 함께 정해진다.")
    @Test
    void resolvesKeyAndRange() {
        AggregationTarget target = AggregationTarget.of("20260722", "WEEKLY");

        assertThat(target.period()).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(target.periodKey()).isEqualTo("2026-W30");
        assertThat(target.from()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(target.to()).isEqualTo(LocalDate.of(2026, 7, 26));
    }

    @DisplayName("월간도 같은 방식으로 정해진다.")
    @Test
    void resolvesMonthly() {
        AggregationTarget target = AggregationTarget.of("20260722", "MONTHLY");

        assertThat(target.periodKey()).isEqualTo("2026-07");
        assertThat(target.from()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(target.to()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @DisplayName("baseDate 형식이 yyyyMMdd 가 아니면 예외를 던진다.")
    @Test
    void rejectsMalformedBaseDate() {
        assertThatThrownBy(() -> AggregationTarget.of("2026-07-22", "WEEKLY"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("baseDate");
    }

    @DisplayName("baseDate 가 없으면 예외를 던진다.")
    @Test
    void rejectsMissingBaseDate() {
        assertThatThrownBy(() -> AggregationTarget.of(null, "WEEKLY"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("baseDate");
    }

    @DisplayName("일간은 MV 적재 대상이 아니므로 거부한다.")
    @Test
    void rejectsDaily() {
        assertThatThrownBy(() -> AggregationTarget.of("20260722", "DAILY"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("일간");
    }
}
