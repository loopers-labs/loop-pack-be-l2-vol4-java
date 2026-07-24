package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodTest {

    // 2026-07-15은 수요일.
    private static final LocalDate WED = LocalDate.of(2026, 7, 15);

    @DisplayName("주간 집계 기간을 계산할 때")
    @Nested
    class Weekly {

        @DisplayName("baseDate가 속한 주의 월요일부터 일요일까지를 범위로 잡는다")
        @Test
        void rangeIsMondayToSunday() {
            // when // then
            assertThat(Period.WEEKLY.from(WED)).isEqualTo(LocalDate.of(2026, 7, 13)); // 월
            assertThat(Period.WEEKLY.to(WED)).isEqualTo(LocalDate.of(2026, 7, 19));   // 일
        }

        @DisplayName("같은 주의 어떤 날이든 같은 key를 주고, 다른 주와는 다른 key를 준다")
        @Test
        void keyIsStableWithinWeek() {
            // given
            String key = Period.WEEKLY.key(WED);

            // then - yyyy-Www 형식, 같은 주(월~일)는 동일, 인접 주는 상이
            assertThat(key).matches("\\d{4}-W\\d{2}");
            assertThat(Period.WEEKLY.key(LocalDate.of(2026, 7, 13))).isEqualTo(key);
            assertThat(Period.WEEKLY.key(LocalDate.of(2026, 7, 19))).isEqualTo(key);
            assertThat(Period.WEEKLY.key(LocalDate.of(2026, 7, 20))).isNotEqualTo(key); // 다음 주 월요일
            assertThat(Period.WEEKLY.key(LocalDate.of(2026, 7, 12))).isNotEqualTo(key); // 지난 주 일요일
        }

        @DisplayName("ISO 주 기준이라 연말·연초가 한 주로 묶이면 week-based-year로 같은 key를 준다")
        @Test
        void keyUsesIsoWeekBasedYearAtBoundary() {
            // 2026-W01 = 월2025-12-29 ~ 일2026-01-04 (달력 연도가 아닌 ISO week-based-year)
            assertThat(Period.WEEKLY.key(LocalDate.of(2025, 12, 29))).isEqualTo("2026-W01");
            assertThat(Period.WEEKLY.key(LocalDate.of(2026, 1, 4))).isEqualTo("2026-W01");
        }
    }

    @DisplayName("월간 집계 기간을 계산할 때")
    @Nested
    class Monthly {

        @DisplayName("baseDate가 속한 달의 1일부터 말일까지를 범위로 잡는다")
        @Test
        void rangeIsFirstToLastDay() {
            // when // then
            assertThat(Period.MONTHLY.from(WED)).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(Period.MONTHLY.to(WED)).isEqualTo(LocalDate.of(2026, 7, 31));
        }

        @DisplayName("key는 yyyy-MM 형식으로 그 달을 가리킨다")
        @Test
        void keyIsYearMonth() {
            // when // then
            assertThat(Period.MONTHLY.key(WED)).isEqualTo("2026-07");
        }
    }
}
