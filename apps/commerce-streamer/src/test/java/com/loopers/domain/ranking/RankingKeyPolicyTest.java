package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingKeyPolicyTest {

  @DisplayName("이벤트 시각을 서울 날짜와 시간으로 변환해 일간·시간 랭킹 키를 만든다.")
  @Test
  void buildsDailyAndHourlyKeysAtSeoulMidnightBoundary() {
    RankingKeys beforeMidnight = RankingKeyPolicy.dailyKeys(Instant.parse("2026-07-15T14:59:59Z"));
    RankingKeys afterMidnight = RankingKeyPolicy.dailyKeys(Instant.parse("2026-07-15T15:00:00Z"));

    assertThat(beforeMidnight)
        .isEqualTo(
            new RankingKeys(
                "ranking:all:20260715", "ranking:hour:2026071523", "ranking:processed:20260715"));
    assertThat(afterMidnight)
        .isEqualTo(
            new RankingKeys(
                "ranking:all:20260716", "ranking:hour:2026071600", "ranking:processed:20260716"));
  }

  @DisplayName("같은 날짜에서도 이벤트 발생 시간이 바뀌면 시간 랭킹 키만 달라진다.")
  @Test
  void separatesHourlyKeysWithinSameDay() {
    RankingKeys tenOClock = RankingKeyPolicy.dailyKeys(Instant.parse("2026-07-16T01:59:59Z"));
    RankingKeys elevenOClock = RankingKeyPolicy.dailyKeys(Instant.parse("2026-07-16T02:00:00Z"));

    assertThat(tenOClock.rankingKey()).isEqualTo(elevenOClock.rankingKey());
    assertThat(tenOClock.processedEventKey()).isEqualTo(elevenOClock.processedEventKey());
    assertThat(tenOClock.hourlyRankingKey()).isEqualTo("ranking:hour:2026071610");
    assertThat(elevenOClock.hourlyRankingKey()).isEqualTo("ranking:hour:2026071611");
  }
}
