package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("periodKey 는 commerce-batch 가 MV 에 적재한 키와 동일한 규칙으로 산출된다.")
class RankingPeriodTest {

    @DisplayName("주간은 ISO 주 기준 yyyy'W'ww 다.")
    @Test
    void weeklyKeyIsIsoWeek() {
        // arrange
        LocalDate thursday = LocalDate.of(2026, 7, 23);

        // act
        String periodKey = RankingPeriod.WEEKLY.periodKey(thursday);

        // assert
        assertThat(periodKey).isEqualTo("2026W30");
    }

    @DisplayName("주간은 연말/연초 경계에서도 ISO week-based year 를 따른다.")
    @Test
    void weeklyKeyFollowsWeekBasedYear() {
        // arrange
        LocalDate newYearsDay = LocalDate.of(2027, 1, 1);  // 2026년 마지막 ISO 주(12/28~1/3)에 속함

        // act
        String periodKey = RankingPeriod.WEEKLY.periodKey(newYearsDay);

        // assert
        assertThat(periodKey).isEqualTo("2026W53");
    }

    @DisplayName("월간은 yyyyMM 이다.")
    @Test
    void monthlyKeyIsYearMonth() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 23);

        // act
        String periodKey = RankingPeriod.MONTHLY.periodKey(date);

        // assert
        assertThat(periodKey).isEqualTo("202607");
    }

    @DisplayName("일간은 Redis ZSET 키와 같은 yyyyMMdd 다.")
    @Test
    void dailyKeyIsBasicIsoDate() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 23);

        // act
        String periodKey = RankingPeriod.DAILY.periodKey(date);

        // assert
        assertThat(periodKey).isEqualTo("20260723");
    }
}