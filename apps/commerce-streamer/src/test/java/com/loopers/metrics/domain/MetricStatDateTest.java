package com.loopers.metrics.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 집계 날짜는 처리시각이 아니라 발생시각(KST)으로 정한다. 오래된 것은 받고, 없거나 미래인 것만 거른다. */
class MetricStatDateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 23);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("발생시각의 KST 날짜를 집계 날짜로 쓴다")
    void givenOccurredAt_whenResolved_thenUsesSeoulDate() {
        ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 22, 15, 0, 0, 0, SEOUL);

        assertThat(MetricStatDate.of(occurredAt, TODAY)).contains(LocalDate.of(2026, 7, 22));
    }

    @Test
    @DisplayName("자정 직전 이벤트가 자정 넘어 처리돼도 발생일에 쌓인다")
    void givenEventJustBeforeMidnight_whenResolvedNextDay_thenBucketedToOccurredDate() {
        ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 22, 23, 59, 58, 0, SEOUL);

        assertThat(MetricStatDate.of(occurredAt, TODAY)).contains(LocalDate.of(2026, 7, 22));
    }

    @Test
    @DisplayName("다른 타임존으로 온 시각도 KST 로 환산해 판정한다")
    void givenOtherZone_whenResolved_thenConvertedToSeoul() {
        ZonedDateTime utc = ZonedDateTime.of(2026, 7, 22, 16, 0, 0, 0, ZoneId.of("UTC"));

        assertThat(MetricStatDate.of(utc, TODAY)).contains(LocalDate.of(2026, 7, 23));
    }

    @Test
    @DisplayName("랭킹 창(2일)보다 오래된 이벤트도 그대로 받는다 — 배치가 과거 기간을 다시 집계한다")
    void givenVeryOldEvent_whenResolved_thenStillAccepted() {
        ZonedDateTime occurredAt = ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, SEOUL);

        assertThat(MetricStatDate.of(occurredAt, TODAY)).contains(LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("발생시각이 없으면 날짜를 정할 수 없다")
    void givenNullOccurredAt_whenResolved_thenEmpty() {
        assertThat(MetricStatDate.of(null, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("미래 날짜는 존재하지 않는 집계라 거른다")
    void givenFutureOccurredAt_whenResolved_thenEmpty() {
        ZonedDateTime tomorrow = ZonedDateTime.of(2026, 7, 24, 0, 0, 0, 0, SEOUL);

        assertThat(MetricStatDate.of(tomorrow, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("오늘은 미래가 아니므로 받는다")
    void givenTodayOccurredAt_whenResolved_thenAccepted() {
        ZonedDateTime today = ZonedDateTime.of(2026, 7, 23, 23, 0, 0, 0, SEOUL);

        assertThat(MetricStatDate.of(today, TODAY)).contains(TODAY);
    }
}
