package com.loopers.infrastructure.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeysTest {

    @DisplayName("날짜로 키를 만들 때, ")
    @Nested
    class Of {
        @DisplayName("ranking:all: 접두사에 yyyyMMdd 형식 날짜를 붙인 키를 반환한다.")
        @Test
        void returnsFormattedKey() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);

            // act
            String key = RankingKeys.of(date);

            // assert
            assertThat(key).isEqualTo("ranking:all:20250906");
        }
    }

    @DisplayName("오늘 날짜 키를 만들 때, ")
    @Nested
    class Today {
        @DisplayName("LocalDate.now() 기준으로 만든 키와 같다.")
        @Test
        void returnsKeyForCurrentDate() {
            // act
            String today = RankingKeys.today();

            // assert
            assertThat(today).isEqualTo(RankingKeys.of(LocalDate.now()));
        }
    }
}
