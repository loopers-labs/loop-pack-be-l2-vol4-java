package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeyTest {

    @DisplayName("일별 랭킹 키는 ranking:all:yyyyMMdd 포맷을 따른다.")
    @Test
    void formatsAsRankingAllYyyyMmDd() {
        // given
        LocalDate date = LocalDate.of(2025, 9, 6);

        // when
        RankingKey key = RankingKey.of(date);

        // then
        assertThat(key.value()).isEqualTo("ranking:all:20250906");
    }

    @DisplayName("랭킹 키의 TTL 은 2일이다.")
    @Test
    void ttlIsTwoDays() {
        // given
        RankingKey key = RankingKey.of(LocalDate.of(2025, 9, 6));

        // when
        Duration ttl = key.ttl();

        // then
        assertThat(ttl).isEqualTo(Duration.ofDays(2));
    }
}
