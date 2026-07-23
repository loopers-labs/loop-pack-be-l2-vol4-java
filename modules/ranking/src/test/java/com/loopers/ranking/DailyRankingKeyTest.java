package com.loopers.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DailyRankingKeyTest {
    @DisplayName("UTC 이벤트 시각을 한국 날짜로 변환해 일간 랭킹 키를 만든다.")
    @Test
    void createsDailyKeyWithKoreanDate() {
        // arrange
        ZonedDateTime occurredAt = ZonedDateTime.parse("2026-07-16T15:30:00Z");

        // act
        String key = DailyRankingKey.from(occurredAt);

        // assert
        assertThat(key).isEqualTo("ranking:all:20260717");
    }

    @DisplayName("일간 키와 상품 member 표현을 고정한다.")
    @Test
    void createsKeyAndMember() {
        assertThat(DailyRankingKey.from(LocalDate.of(2026, 7, 17))).isEqualTo("ranking:all:20260717");
        assertThat(DailyRankingKey.member(123L)).isEqualTo("123");
    }
}
