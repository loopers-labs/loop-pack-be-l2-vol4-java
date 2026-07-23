package com.loopers.tddstudy.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeyTest {

    @Test
    @DisplayName("날짜로 일간 랭킹 키를 만든다")
    void dailyKey() {
        String key = RankingKey.daily(LocalDate.of(2026, 7, 14));

        assertThat(key).isEqualTo("ranking:all:20260714");
    }

    @Test
    @DisplayName("epoch millis는 서울 기준 날짜로 변환된다")
    void epochMillisToSeoulDate() {
        // 2026-07-14 00:30 KST = 2026-07-13 15:30 UTC → UTC로 계산하면 전날이 나옴
        long occurredAt = ZonedDateTime.of(2026, 7, 14, 0, 30, 0, 0,
                ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();

        assertThat(RankingKey.dateOf(occurredAt))
                .isEqualTo(LocalDate.of(2026, 7, 14));
    }
}
