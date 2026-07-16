package com.loopers.infrastructure.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingRedisKeysTest {

    @DisplayName("일자를 yyyyMMdd 형식으로 반영한 랭킹 키를 생성한다.")
    @Test
    void generatesDailyKey_withYyyyMMddFormat() {
        // given
        LocalDate date = LocalDate.of(2026, 7, 16);

        // when
        String key = RankingRedisKeys.dailyKey(date);

        // then
        assertThat(key).isEqualTo("ranking:all:20260716");
    }

    @DisplayName("날짜가 다르면 서로 다른 키가 생성된다.")
    @Test
    void generatesDifferentKeys_whenDatesDiffer() {
        // given
        LocalDate today = LocalDate.of(2026, 7, 16);
        LocalDate yesterday = LocalDate.of(2026, 7, 15);

        // when
        String todayKey = RankingRedisKeys.dailyKey(today);
        String yesterdayKey = RankingRedisKeys.dailyKey(yesterday);

        // then
        assertThat(todayKey).isNotEqualTo(yesterdayKey);
    }
}
