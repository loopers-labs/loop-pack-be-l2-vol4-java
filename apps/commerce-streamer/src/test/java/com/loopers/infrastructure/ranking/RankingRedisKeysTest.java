package com.loopers.infrastructure.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @DisplayName("일시를 yyyyMMddHH 형식으로 반영한 시간별 랭킹 키를 생성한다.")
    @Test
    void generatesHourlyKey_withYyyyMMddHHFormat() {
        // given
        LocalDateTime dateTime = LocalDateTime.of(2026, 7, 16, 23, 30);

        // when
        String key = RankingRedisKeys.hourlyKey(dateTime);

        // then
        assertThat(key).isEqualTo("ranking:hourly:2026071623");
    }

    @DisplayName("같은 시간대 안에서는 분이 달라도 같은 키가 생성된다.")
    @Test
    void generatesSameHourlyKey_whenWithinSameHour() {
        // given
        LocalDateTime early = LocalDateTime.of(2026, 7, 16, 23, 1);
        LocalDateTime late = LocalDateTime.of(2026, 7, 16, 23, 59);

        // when
        String earlyKey = RankingRedisKeys.hourlyKey(early);
        String lateKey = RankingRedisKeys.hourlyKey(late);

        // then
        assertThat(earlyKey).isEqualTo(lateKey);
    }

    @DisplayName("시간대가 다르면 서로 다른 시간별 키가 생성된다.")
    @Test
    void generatesDifferentHourlyKeys_whenHoursDiffer() {
        // given
        LocalDateTime thisHour = LocalDateTime.of(2026, 7, 16, 23, 0);
        LocalDateTime nextHour = LocalDateTime.of(2026, 7, 17, 0, 0);

        // when
        String thisHourKey = RankingRedisKeys.hourlyKey(thisHour);
        String nextHourKey = RankingRedisKeys.hourlyKey(nextHour);

        // then
        assertThat(thisHourKey).isNotEqualTo(nextHourKey);
    }
}
