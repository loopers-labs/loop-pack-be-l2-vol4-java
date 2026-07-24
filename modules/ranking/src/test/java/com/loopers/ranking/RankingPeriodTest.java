package com.loopers.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingPeriodTest {

    @DisplayName("기준 날짜가 속한 일간·주간·월간의 시작일과 종료일을 계산한다")
    @MethodSource("periodRanges")
    @ParameterizedTest
    void calculatesCalendarPeriodRange(
        RankingPeriod period,
        LocalDate date,
        LocalDate expectedStart,
        LocalDate expectedEnd
    ) {
        // act
        LocalDate periodStart = period.periodStart(date);
        LocalDate periodEnd = period.periodEnd(date);

        // assert
        assertAll(
            () -> assertThat(periodStart).isEqualTo(expectedStart),
            () -> assertThat(periodEnd).isEqualTo(expectedEnd)
        );
    }

    private static Stream<Arguments> periodRanges() {
        return Stream.of(
            Arguments.of(
                RankingPeriod.DAILY,
                LocalDate.of(2026, 7, 19),
                LocalDate.of(2026, 7, 19),
                LocalDate.of(2026, 7, 19)
            ),
            Arguments.of(
                RankingPeriod.WEEKLY,
                LocalDate.of(2026, 7, 19),
                LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 19)
            ),
            Arguments.of(
                RankingPeriod.WEEKLY,
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 12, 28),
                LocalDate.of(2027, 1, 3)
            ),
            Arguments.of(
                RankingPeriod.MONTHLY,
                LocalDate.of(2028, 2, 14),
                LocalDate.of(2028, 2, 1),
                LocalDate.of(2028, 2, 29)
            )
        );
    }
}
