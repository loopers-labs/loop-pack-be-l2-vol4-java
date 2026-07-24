package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기간 경계 환산 규칙 검증. commerce-batch 의 {@code RankingPeriodType} 과 <b>같은 결과</b>가 나와야
 * 배치가 적재한 {@code period_start} 로 조회가 맞아떨어진다.
 */
class RankingPeriodTest {

    @DisplayName("주간은 ISO-8601 월요일 시작")
    @Nested
    class Weekly {

        @DisplayName("주 중간 날짜는 그 주 월요일로 환산된다.")
        @Test
        void midWeekResolvesToMonday() {
            // 2026-07-23 은 목요일 → 그 주 월요일 = 07-20
            LocalDate start = RankingPeriod.WEEKLY.resolveStart(LocalDate.of(2026, 7, 23));
            assertThat(start).isEqualTo(LocalDate.of(2026, 7, 20));
            assertThat(RankingPeriod.WEEKLY.resolveEnd(start)).isEqualTo(LocalDate.of(2026, 7, 26));
        }

        @DisplayName("월요일은 자기 자신이 시작일이다.")
        @Test
        void mondayIsItsOwnStart() {
            LocalDate monday = LocalDate.of(2026, 7, 20);
            assertThat(RankingPeriod.WEEKLY.resolveStart(monday)).isEqualTo(monday);
        }

        @DisplayName("일요일은 이전 월요일에 속한다 — 일요일 시작이 아니다.")
        @Test
        void sundayBelongsToPreviousMonday() {
            // 2026-07-26 은 일요일. 일요일 시작 규칙이면 07-26 이 시작이 되어버린다.
            LocalDate start = RankingPeriod.WEEKLY.resolveStart(LocalDate.of(2026, 7, 26));
            assertThat(start).isEqualTo(LocalDate.of(2026, 7, 20));
        }

        @DisplayName("연말 주는 해를 넘겨 이어진다.")
        @Test
        void weekSpansYearBoundary() {
            // 2027-01-01 은 금요일 → 그 주 월요일은 2026-12-28
            LocalDate start = RankingPeriod.WEEKLY.resolveStart(LocalDate.of(2027, 1, 1));
            assertThat(start).isEqualTo(LocalDate.of(2026, 12, 28));
            assertThat(RankingPeriod.WEEKLY.resolveEnd(start)).isEqualTo(LocalDate.of(2027, 1, 3));
        }
    }

    @DisplayName("월간은 1일 ~ 말일")
    @Nested
    class Monthly {

        @DisplayName("달 중간 날짜는 그 달 1일로 환산된다.")
        @Test
        void midMonthResolvesToFirstDay() {
            LocalDate start = RankingPeriod.MONTHLY.resolveStart(LocalDate.of(2026, 7, 23));
            assertThat(start).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(RankingPeriod.MONTHLY.resolveEnd(start)).isEqualTo(LocalDate.of(2026, 7, 31));
        }

        @DisplayName("30일까지인 달의 말일을 맞춘다.")
        @Test
        void handlesThirtyDayMonth() {
            LocalDate start = RankingPeriod.MONTHLY.resolveStart(LocalDate.of(2026, 6, 15));
            assertThat(RankingPeriod.MONTHLY.resolveEnd(start)).isEqualTo(LocalDate.of(2026, 6, 30));
        }

        @DisplayName("윤년 2월의 말일은 29일이다.")
        @Test
        void handlesLeapFebruary() {
            LocalDate start = RankingPeriod.MONTHLY.resolveStart(LocalDate.of(2028, 2, 10));
            assertThat(RankingPeriod.MONTHLY.resolveEnd(start)).isEqualTo(LocalDate.of(2028, 2, 29));
        }
    }

    @DisplayName("일간은 시작=종료=그 날짜다.")
    @Test
    void dailyIsSingleDay() {
        LocalDate date = LocalDate.of(2026, 7, 23);
        assertThat(RankingPeriod.DAILY.resolveStart(date)).isEqualTo(date);
        assertThat(RankingPeriod.DAILY.resolveEnd(date)).isEqualTo(date);
        assertThat(RankingPeriod.DAILY.isDaily()).isTrue();
        assertThat(RankingPeriod.WEEKLY.isDaily()).isFalse();
    }

    @DisplayName("요청 파라미터 해석")
    @Nested
    class From {

        @DisplayName("비어 있으면 DAILY — period 없이 호출하던 기존 클라이언트 호환.")
        @Test
        void blankDefaultsToDaily() {
            assertThat(RankingPeriod.from(null)).isEqualTo(RankingPeriod.DAILY);
            assertThat(RankingPeriod.from("")).isEqualTo(RankingPeriod.DAILY);
            assertThat(RankingPeriod.from("  ")).isEqualTo(RankingPeriod.DAILY);
        }

        @DisplayName("대소문자와 공백을 가리지 않는다.")
        @Test
        void isCaseInsensitive() {
            assertThat(RankingPeriod.from("weekly")).isEqualTo(RankingPeriod.WEEKLY);
            assertThat(RankingPeriod.from("Monthly")).isEqualTo(RankingPeriod.MONTHLY);
            assertThat(RankingPeriod.from(" DAILY ")).isEqualTo(RankingPeriod.DAILY);
        }

        @DisplayName("알 수 없는 값은 조용히 넘기지 않고 실패시킨다.")
        @Test
        void rejectsUnknown() {
            assertThatThrownBy(() -> RankingPeriod.from("yearly"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("yearly");
        }
    }
}
