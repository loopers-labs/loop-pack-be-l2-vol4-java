package com.loopers.job.ranking.period;

import com.loopers.batch.job.ranking.period.PeriodRange;
import com.loopers.batch.job.ranking.period.RankingPeriodType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배치 대상 구간 해석 검증.
 *
 * <p>경계 규칙은 commerce-api 의 {@code RankingPeriod} 와 동일해야 한다 — 배치가 적재한
 * {@code period_start} 로 API 가 조회하기 때문이다. 한쪽만 바뀌면 조회가 영영 빈 결과를 낸다.
 */
class PeriodRangeTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @DisplayName("baseDate 를 주면 그 날짜가 속한 기간을 잡는다 — 주간.")
    @Test
    void resolvesWeeklyFromBaseDate() {
        // 2026-07-23(목) → 그 주 월요일 07-20 ~ 일요일 07-26
        PeriodRange range = PeriodRange.resolve(RankingPeriodType.WEEKLY, "20260723");

        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 7, 26));
    }

    @DisplayName("baseDate 를 주면 그 날짜가 속한 기간을 잡는다 — 월간.")
    @Test
    void resolvesMonthlyFromBaseDate() {
        PeriodRange range = PeriodRange.resolve(RankingPeriodType.MONTHLY, "20260723");

        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @DisplayName("baseDate 가 없으면 직전 확정 기간(지난주)을 잡는다 — 진행 중인 주를 집계하지 않는다.")
    @Test
    void fallsBackToPreviousWeek() {
        PeriodRange range = PeriodRange.resolve(RankingPeriodType.WEEKLY, null);

        LocalDate thisMonday = LocalDate.now(KST).with(java.time.DayOfWeek.MONDAY);
        assertThat(range.start()).isEqualTo(thisMonday.minusWeeks(1));
        assertThat(range.end()).isEqualTo(range.start().plusDays(6));
        // 이번 주는 아직 안 끝났으므로 대상이 아니다
        assertThat(range.end()).isBefore(thisMonday);
    }

    @DisplayName("baseDate 가 없으면 직전 확정 기간(지난달)을 잡는다.")
    @Test
    void fallsBackToPreviousMonth() {
        PeriodRange range = PeriodRange.resolve(RankingPeriodType.MONTHLY, null);

        LocalDate thisMonthFirst = LocalDate.now(KST).withDayOfMonth(1);
        assertThat(range.start()).isEqualTo(thisMonthFirst.minusMonths(1));
        assertThat(range.end()).isBefore(thisMonthFirst);
    }

    @DisplayName("공백 문자열도 없는 것으로 본다.")
    @Test
    void blankIsTreatedAsAbsent() {
        PeriodRange blank = PeriodRange.resolve(RankingPeriodType.WEEKLY, "  ");
        PeriodRange nullish = PeriodRange.resolve(RankingPeriodType.WEEKLY, null);

        assertThat(blank).isEqualTo(nullish);
    }

    @DisplayName("staging 구분자와 MV 테이블명이 기간별로 다르다.")
    @Test
    void exposesTypeMetadata() {
        assertThat(RankingPeriodType.WEEKLY.code()).isEqualTo("WEEKLY");
        assertThat(RankingPeriodType.WEEKLY.mvTable()).isEqualTo("mv_product_rank_weekly");
        assertThat(RankingPeriodType.MONTHLY.mvTable()).isEqualTo("mv_product_rank_monthly");
    }
}
