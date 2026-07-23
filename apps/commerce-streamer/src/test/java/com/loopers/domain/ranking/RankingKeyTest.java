package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingKeyTest {

    @DisplayName("날짜를 주면 ranking:all:{yyyyMMdd} 형식의 일간 키가 계산된다.")
    @Test
    void buildsDailyKey_fromDate() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 15);

        // act
        String key = RankingKey.daily(date);

        // assert
        assertThat(key).isEqualTo("ranking:all:20260715");
    }

    @DisplayName("한 자리 월·일은 0이 패딩되어 항상 8자리 날짜로 계산된다.")
    @Test
    void padsSingleDigitMonthAndDay() {
        // arrange
        LocalDate date = LocalDate.of(2026, 1, 3);

        // act
        String key = RankingKey.daily(date);

        // assert
        assertThat(key).isEqualTo("ranking:all:20260103");
    }

    @DisplayName("null 날짜로 키 계산 시 IllegalArgumentException이 발생한다.")
    @Test
    void throwsIllegalArgumentException_whenDateIsNull() {
        // act & assert
        assertThrows(IllegalArgumentException.class, () -> RankingKey.daily(null));
    }
}
