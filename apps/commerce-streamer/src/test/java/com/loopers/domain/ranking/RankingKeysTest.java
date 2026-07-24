package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeysTest {

    @DisplayName("일간 키는 ranking:all:yyyyMMdd 형식이다.")
    @Test
    void dailyKeyFormat() {
        assertThat(RankingKeys.daily(LocalDate.of(2026, 7, 15))).isEqualTo("ranking:all:20260715");
    }

    @DisplayName("dedup 키는 ranking:dedup:{eventId} 형식이다.")
    @Test
    void dedupKeyFormat() {
        assertThat(RankingKeys.dedup("e-1")).isEqualTo("ranking:dedup:e-1");
    }

    @DisplayName("occurredAt(UTC)은 Asia/Seoul 날짜로 양자화된다 — UTC 23:30 은 KST 다음날이다.")
    @Test
    void dateOfConvertsToSeoul() {
        assertThat(RankingKeys.dateOf("2026-07-15T23:30:00Z")).isEqualTo(LocalDate.of(2026, 7, 16));
        assertThat(RankingKeys.dateOf("2026-07-15T03:00:00Z")).isEqualTo(LocalDate.of(2026, 7, 15));
    }
}
