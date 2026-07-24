package com.loopers.ranking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회 쪽 기간 정책. period_key 형식은 commerce-batch 의 RankingPeriod 와 같아야 MV 를 찾을 수 있다
 * (apps 분리라 복제 — 형식이 어긋나면 조회가 빈다).
 * 진행 중인 기간은 완결본이 없으므로 조회 대상이 아니다.
 */
class RankingPeriodTest {

    @Nested
    @DisplayName("주간")
    class Weekly {

        @Test
        @DisplayName("date 가 속한 주의 키를 낸다 — batch 저장 키와 같은 ISO 형식")
        void givenDate_whenPeriodKey_thenIsoWeek() {
            assertThat(RankingPeriod.WEEKLY.periodKey(LocalDate.of(2026, 7, 22))).isEqualTo("2026-W30");
        }

        @Test
        @DisplayName("해를 걸치는 주도 batch 와 같게 낸다")
        void givenYearBoundary_whenPeriodKey_thenMatchesBatch() {
            assertThat(RankingPeriod.WEEKLY.periodKey(LocalDate.of(2025, 12, 29))).isEqualTo("2026-W01");
            assertThat(RankingPeriod.WEEKLY.periodKey(LocalDate.of(2027, 1, 3))).isEqualTo("2026-W53");
        }

        @Test
        @DisplayName("그 주가 끝난 뒤여야 완결이다")
        void givenWeek_whenComplete_thenEndBeforeToday() {
            LocalDate inW30 = LocalDate.of(2026, 7, 22);
            assertThat(RankingPeriod.WEEKLY.isComplete(inW30, LocalDate.of(2026, 7, 27))).isTrue();  // 다음 주 월
            assertThat(RankingPeriod.WEEKLY.isComplete(inW30, LocalDate.of(2026, 7, 26))).isFalse(); // 그 주 일요일 = 진행 중
            assertThat(RankingPeriod.WEEKLY.isComplete(inW30, LocalDate.of(2026, 7, 24))).isFalse();
        }

        @Test
        @DisplayName("date 생략 시 가장 최근 확정된 지난 주 키를 낸다")
        void givenToday_whenLatestComplete_thenPreviousWeek() {
            // 오늘이 7/30(목, W31)이면 가장 최근 확정본은 지난 주 W30
            assertThat(RankingPeriod.WEEKLY.latestCompletedKey(LocalDate.of(2026, 7, 30))).isEqualTo("2026-W30");
        }
    }

    @Nested
    @DisplayName("월간")
    class Monthly {

        @Test
        @DisplayName("date 가 속한 달의 키를 낸다")
        void givenDate_whenPeriodKey_thenMonth() {
            assertThat(RankingPeriod.MONTHLY.periodKey(LocalDate.of(2026, 7, 22))).isEqualTo("2026-07");
        }

        @Test
        @DisplayName("그 달이 끝난 뒤여야 완결이다")
        void givenMonth_whenComplete_thenEndBeforeToday() {
            LocalDate inJuly = LocalDate.of(2026, 7, 15);
            assertThat(RankingPeriod.MONTHLY.isComplete(inJuly, LocalDate.of(2026, 8, 1))).isTrue();
            assertThat(RankingPeriod.MONTHLY.isComplete(inJuly, LocalDate.of(2026, 7, 31))).isFalse(); // 말일 = 진행 중
        }

        @Test
        @DisplayName("date 생략 시 가장 최근 확정된 지난 달 키를 낸다")
        void givenToday_whenLatestComplete_thenPreviousMonth() {
            assertThat(RankingPeriod.MONTHLY.latestCompletedKey(LocalDate.of(2026, 7, 15))).isEqualTo("2026-06");
        }
    }
}
